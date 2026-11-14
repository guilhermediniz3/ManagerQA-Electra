"use strict";
/**
 * Sobe o ambiente inteiro do QA Manager dentro do app:
 *   1. Postgres portable (dados em %APPDATA%\ManagerQA\pgdata)
 *   2. IA local (Ollama embutido), quando vier nesta versão
 *   3. Backend Spring Boot (jar + JRE embutido), que também serve o frontend
 *   4. Janela apontando para o backend local
 * Nada é instalado na máquina além da própria pasta do app.
 */
const { app, BrowserWindow, dialog, ipcMain, shell, Menu } = require("electron");
const path = require("path");
const fs = require("fs");
const net = require("net");
const http = require("http");
const { spawn, spawnSync } = require("child_process");

const runtimeDir = app.isPackaged
  ? path.join(process.resourcesPath, "runtime")
  : path.join(__dirname, "resources");
const pgHome = path.join(runtimeDir, "postgres");
const pgSeed = path.join(runtimeDir, "pgdata-seed");
const javaExe = path.join(runtimeDir, "jre", "bin", "java.exe");
const jarFile = path.join(runtimeDir, "app.jar");
const ollamaExe = path.join(runtimeDir, "ollama", "ollama.exe");
const ollamaSeed = path.join(runtimeDir, "ollama-models");
const configFile = path.join(runtimeDir, "app-config.json");

const dataRoot = path.join(app.getPath("appData"), config().dataProfile || "ManagerQA");
app.setPath("userData", path.join(dataRoot, "browser"));
const pgData = path.join(dataRoot, "pgdata");
// Os modelos ficam no perfil, e não junto do programa: em Arquivos de
// Programas não há permissão de escrita, e sem escrever não há como baixar
// modelo novo pela tela.
const ollamaModels = path.join(dataRoot, "ollama-models");
const logFile = path.join(dataRoot, "manager.log");
const pgLog = path.join(dataRoot, "postgres.log");

let backend = null;
let window = null;
let pgPort = 0;
let apiPort = 0;
let shuttingDown = false;
let ownsPostgres = false;
let ollama = null;
let ollamaUrl = "";

function log(line) {
  const stamped = `[${new Date().toISOString()}] ${line}\n`;
  try {
    fs.appendFileSync(logFile, stamped);
  } catch {
    /* primeira execução: diretório ainda não existe */
  }
  process.stdout.write(stamped);
}

function config() {
  try {
    // O PowerShell grava UTF-8 com BOM; JSON.parse recusa o BOM.
    const raw = fs.readFileSync(configFile, "utf8").replace(/^﻿/, "");
    return JSON.parse(raw);
  } catch (error) {
    throw new Error(
      "Configuração do app não encontrada em " + configFile + ": " + error.message,
    );
  }
}

function freePort(preferred) {
  const tryPort = (port) =>
    new Promise((resolve) => {
      const probe = net.createServer();
      probe.once("error", () => resolve(false));
      probe.once("listening", () => probe.close(() => resolve(true)));
      probe.listen(port, "127.0.0.1");
    });
  return (async () => {
    for (let port = preferred; port < preferred + 50; port++) {
      if (await tryPort(port)) return port;
    }
    throw new Error("Nenhuma porta livre a partir de " + preferred);
  })();
}

/**
 * Subpastas do PGDATA que o Postgres exige e que ficam vazias num banco recém
 * desligado. Empacotadores (7z/NSIS) descartam diretórios vazios, então elas
 * precisam ser recriadas depois de copiar o banco embutido — sem isso o
 * servidor aborta com "could not open directory pg_notify".
 */
const PGDATA_DIRS = [
  "pg_commit_ts",
  "pg_dynshmem",
  "pg_logical",
  "pg_logical/mappings",
  "pg_logical/snapshots",
  "pg_multixact",
  "pg_multixact/members",
  "pg_multixact/offsets",
  "pg_notify",
  "pg_replslot",
  "pg_serial",
  "pg_snapshots",
  "pg_stat",
  "pg_stat_tmp",
  "pg_subtrans",
  "pg_tblspc",
  "pg_twophase",
  "pg_wal",
  "pg_wal/archive_status",
  "pg_wal/summaries",
  "pg_xact",
];

function ensurePgDataDirs() {
  let created = 0;
  for (const relative of PGDATA_DIRS) {
    const full = path.join(pgData, relative);
    if (!fs.existsSync(full)) {
      fs.mkdirSync(full, { recursive: true });
      created++;
    }
  }
  if (created > 0) log("Recriadas " + created + " subpastas do banco");
}

function prepareDataDir() {
  fs.mkdirSync(dataRoot, { recursive: true });
  if (!fs.existsSync(pgData)) {
    log("Primeira execução: instalando o banco em " + pgData);
    fs.cpSync(pgSeed, pgData, { recursive: true });
  }
  // Um postmaster.pid herdado da máquina de origem impede o start.
  const stalePid = path.join(pgData, "postmaster.pid");
  if (fs.existsSync(stalePid)) {
    const running = spawnSync(path.join(pgHome, "bin", "pg_ctl.exe"), ["-D", pgData, "status"], { stdio: "ignore", windowsHide: true });
    if (running.status === 0) throw new Error("O banco desta versão já está em uso. Feche a outra instância antes de continuar.");
    fs.rmSync(stalePid, { force: true });
    log("postmaster.pid órfão removido");
  }
  ensurePgDataDirs();
}

function startPostgres() {
  // stdio ignorado de propósito: no Windows o pg_ctl lança o postgres.exe como
  // processo neto que herda os pipes do pai. Com stdio em pipe, o pg_ctl termina
  // mas o pipe fica aberto pelo servidor e o spawnSync nunca retorna.
  const result = spawnSync(
    path.join(pgHome, "bin", "pg_ctl.exe"),
    ["-D", pgData, "-l", pgLog, "-o", `-p ${pgPort} -h 127.0.0.1`, "-w", "-t", "60", "start"],
    { stdio: "ignore", windowsHide: true },
  );
  if (result.status !== 0) {
    let tail = "Veja " + pgLog;
    try {
      const recent = fs.readFileSync(pgLog, "utf8").split("\n");
      tail = recent.slice(-8).join("\n").trim() || tail;
    } catch {
      /* log ainda nao existe: mantem o caminho do arquivo na mensagem */
    }
    throw new Error(
      "Postgres nao subiu (codigo " + result.status + ")." + "\n" + tail,
    );
  }
  ownsPostgres = true;
  log("Postgres ouvindo na porta " + pgPort);
}

function stopPostgres() {
  if (!ownsPostgres) return;
  spawnSync(
    path.join(pgHome, "bin", "pg_ctl.exe"),
    ["-D", pgData, "-m", "fast", "-w", "-t", "30", "stop"],
    { stdio: "ignore", windowsHide: true },
  );
  log("Postgres encerrado");
}

/** Alguém já está servindo IA nesta porta? */
function iaRespondeEm(port) {
  return new Promise((resolve) => {
    const request = http.get(
      { host: "127.0.0.1", port, path: "/api/tags", timeout: 1500 },
      (response) => {
        response.resume();
        resolve(response.statusCode === 200);
      },
    );
    request.on("error", () => resolve(false));
    request.on("timeout", () => {
      request.destroy();
      resolve(false);
    });
  });
}

/**
 * Leva para o perfil o modelo que veio no instalador.
 *
 * É cópia, e não uso direto da pasta do programa, porque quem quiser trocar de
 * modelo depois precisa de uma pasta onde se possa escrever. Acontece uma vez:
 * havendo modelo no perfil, a semente é ignorada.
 */
function semearModelos() {
  if (!fs.existsSync(ollamaSeed)) return;
  const blobs = path.join(ollamaModels, "blobs");
  if (fs.existsSync(blobs) && fs.readdirSync(blobs).length > 0) return;
  log("Primeira execução: instalando o modelo de IA em " + ollamaModels);
  fs.cpSync(ollamaSeed, ollamaModels, { recursive: true });
}

/**
 * Sobe a IA local, se ela veio nesta versão.
 *
 * Um serviço que já esteja de pé na porta padrão é aproveitado em vez de
 * concorrer com ele — é o caso de quem instalou o Ollama por fora. A falha
 * aqui não derruba o app: sem IA local o sistema continua inteiro, só com os
 * providers de nuvem.
 */
async function startOllama() {
  try {
    if (!fs.existsSync(ollamaExe)) {
      log("IA local não veio nesta versão");
      return;
    }
    if (await iaRespondeEm(11434)) {
      ollamaUrl = "http://127.0.0.1:11434";
      log("IA local já estava em execução em " + ollamaUrl);
      return;
    }
    semearModelos();
    const port = await freePort(11434);
    ollama = spawn(ollamaExe, ["serve"], {
      env: {
        ...process.env,
        OLLAMA_HOST: "127.0.0.1:" + port,
        OLLAMA_MODELS: ollamaModels,
      },
      windowsHide: true,
      stdio: "ignore",
    });
    ollama.on("exit", (code) => log("IA local encerrou com código " + code));
    ollamaUrl = "http://127.0.0.1:" + port;
    log("IA local subindo em " + ollamaUrl);
  } catch (error) {
    log("IA local não subiu: " + error.message);
  }
}

/**
 * Encerra a IA local junto com o app.
 *
 * Mata a árvore inteira: o serviço lança um processo separado para cada modelo
 * carregado, e matar só o pai deixaria esses filhos ocupando memória.
 */
function stopOllama() {
  if (!ollama || ollama.killed) return;
  spawnSync("taskkill", ["/PID", String(ollama.pid), "/T", "/F"], {
    stdio: "ignore",
    windowsHide: true,
  });
  log("IA local encerrada");
}

function startBackend() {
  const settings = config();
  backend = spawn(
    javaExe,
    ["-Xmx512m", "-Dspring.devtools.restart.enabled=false", "-jar", jarFile],
    {
      cwd: dataRoot,
      env: {
        ...process.env,
        SPRING_CONFIG_IMPORT: "",
        SERVER_PORT: String(apiPort),
        // Somente loopback: sem isso o Tomcat escuta em todas as interfaces,
        // expondo a API na rede local e disparando o aviso do firewall do
        // Windows na primeira execucao de uma maquina nova.
        SERVER_ADDRESS: "127.0.0.1",
        DB_URL: `jdbc:postgresql://127.0.0.1:${pgPort}/${settings.dbName}`,
        DB_USER: settings.dbUser,
        DB_PASSWORD: settings.dbPassword,
        DB_DDL_AUTO: "update",
        JWT_SECRET: settings.jwtSecret,
        INTEGRATION_MASTER_KEY: settings.integrationMasterKey,
        CORS_ORIGINS: `http://127.0.0.1:${apiPort}`,
        // A porta da IA local é escolhida aqui; sem avisar o backend, ele
        // procuraria na porta padrão e não acharia quando ela estiver ocupada.
        OLLAMA_BASE_URL: ollamaUrl,
      },
    },
  );
  backend.stdout.on("data", (chunk) => log("[api] " + String(chunk).trimEnd()));
  backend.stderr.on("data", (chunk) => log("[api] " + String(chunk).trimEnd()));
  backend.on("exit", (code) => {
    log("Backend encerrou com código " + code);
    if (!shuttingDown && code !== 0) {
      dialog.showErrorBox(
        "O serviço do QA Manager parou",
        "O backend encerrou inesperadamente (código " + code + ").\n\n" +
          "O log completo está em:\n" + logFile,
      );
      app.quit();
    }
  });
}

function waitForApi(timeoutMs = 240000) {
  const started = Date.now();
  return new Promise((resolve, reject) => {
    const attempt = () => {
      const request = http.get(
        { host: "127.0.0.1", port: apiPort, path: "/actuator/health", timeout: 3000 },
        (response) => {
          response.resume();
          if (response.statusCode === 200) return resolve();
          retry();
        },
      );
      request.on("error", retry);
      request.on("timeout", () => request.destroy());
    };
    const retry = () => {
      if (Date.now() - started > timeoutMs) {
        return reject(new Error("O backend não respondeu em " + timeoutMs / 1000 + "s"));
      }
      setTimeout(attempt, 1000);
    };
    attempt();
  });
}

/**
 * Seletor de pastas para a interface.
 *
 * O RunManager precisa do caminho do projeto de automação, e navegador nenhum
 * entrega o caminho absoluto de uma pasta — por segurança, e com razão. Dentro
 * do aplicativo isso é possível, então o campo ganha um "Procurar" em vez de
 * depender de alguém digitar o caminho sem errar.
 */
function registrarSeletorDePasta() {
  ipcMain.handle("escolher-pasta", async (_evento, inicial) => {
    const escolha = await dialog.showOpenDialog(window, {
      title: "Escolha a pasta do projeto de automação",
      defaultPath: inicial || undefined,
      properties: ["openDirectory"],
    });
    return escolha.canceled || escolha.filePaths.length === 0 ? null : escolha.filePaths[0];
  });
}

/**
 * Abre um arquivo no programa padrão do sistema.
 *
 * Usado pelo vídeo da falha. Só abre o que existe e o que é arquivo — a
 * interface é local, mas o caminho vem do banco, e abrir qualquer coisa que
 * chegue por ali seria confiar demais no caminho.
 */
function registrarAberturaDeArquivo() {
  ipcMain.handle("abrir-arquivo", async (_evento, caminho) => {
    if (typeof caminho !== "string" || !caminho.trim()) return false;
    try {
      const alvo = path.resolve(caminho);
      if (!fs.existsSync(alvo) || !fs.statSync(alvo).isFile()) return false;
      const erro = await shell.openPath(alvo);
      return !erro;
    } catch {
      return false;
    }
  });
}

const { registerScreenCapture } = require("./capture");

function createWindow() {
  window = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 1024,
    minHeight: 640,
    show: false,
    backgroundColor: "#0b0d17",
    title: "QA Manager",
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
      // O Chromium estrangula timers de janela minimizada; sem isto o
      // renovador de sessao para e o usuario volta deslogado.
      backgroundThrottling: false,
    },
  });
  registerScreenCapture(window, () => "http://127.0.0.1:" + apiPort, log);
  window.once("ready-to-show", () => window.show());
  window.loadFile(path.join(__dirname, "loading.html"));
  // Links externos (Jira, Vimeo) abrem no navegador do sistema, não dentro do app.
  window.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:\/\//i.test(url)) shell.openExternal(url);
    return { action: "deny" };
  });
  return window;
}

async function boot() {
  fs.mkdirSync(dataRoot, { recursive: true });
  log("=== QA Manager iniciando ===");
  createWindow();
  try {
    prepareDataDir();
    pgPort = await freePort(55432);
    apiPort = await freePort(18081);
    startPostgres();
    await startOllama();
    startBackend();
    await waitForApi();
    log("Backend pronto em http://127.0.0.1:" + apiPort);
    await window.loadURL("http://127.0.0.1:" + apiPort + "/");
  } catch (error) {
    log("FALHA: " + error.message);
    dialog.showErrorBox(
      "Não foi possível iniciar o QA Manager",
      error.message + "\n\nO log completo está em:\n" + logFile,
    );
    app.quit();
  }
}

function shutdown() {
  if (shuttingDown) return;
  shuttingDown = true;
  log("Encerrando…");
  if (backend && !backend.killed) backend.kill();
  try {
    stopOllama();
  } catch (error) {
    log("Falha ao encerrar a IA local: " + error.message);
  }
  try {
    stopPostgres();
  } catch (error) {
    log("Falha ao encerrar o Postgres: " + error.message);
  }
}

const single = app.requestSingleInstanceLock();
if (!single) {
  app.quit();
} else {
  app.on("second-instance", () => {
    if (window) {
      if (window.isMinimized()) window.restore();
      window.focus();
    }
  });
  app.whenReady().then(() => {
    Menu.setApplicationMenu(null);
    registrarSeletorDePasta();
    registrarAberturaDeArquivo();
    boot();
  });
  app.on("window-all-closed", () => app.quit());
  app.on("before-quit", shutdown);
  process.on("exit", shutdown);
}

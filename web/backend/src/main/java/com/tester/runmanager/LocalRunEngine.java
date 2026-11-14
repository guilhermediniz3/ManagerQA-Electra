package com.tester.runmanager;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.tester.runmanager.entity.RunExecution;
import com.tester.runmanager.entity.RunSystem;

/**
 * Roda o script na própria máquina, como o painel local sempre fez.
 *
 * O script cadastrado é executado tal como foi escrito, pelo interpretador de
 * comandos do sistema — é para isso que ele existe, e é o mesmo que o painel
 * local faz ao chamar o Playwright. Duas consequências que não dá para ignorar:
 * quem cadastra um card manda um comando para a máquina de quem clicar, e por
 * isso o cadastro fica atrás de sessão autenticada; e o comando roda com as
 * permissões de quem abriu o aplicativo, não mais que isso.
 *
 * Só serve no aplicativo instalado. Num servidor web, "a máquina" é o servidor,
 * e não o computador de quem clicou — por isso este motor só se oferece quando
 * o sistema tem caminho local configurado.
 */
@Component
public class LocalRunEngine implements RunEngine {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LocalRunEngine.class);

    private final Map<Long, Process> emAndamento = new ConcurrentHashMap<>();

    @Override
    public String nome() {
        return "local";
    }

    @Override
    public boolean atende(RunSystem sistema) {
        return sistema != null && sistema.getLocalPath() != null && !sistema.getLocalPath().isBlank();
    }

    @Override
    public void disparar(RunExecution execucao, RunSystem sistema,
                         Map<String, String> variaveis, Registro registro) {
        Long id = execucao.getId();
        String script = execucao.getScript();
        File pasta = new File(sistema.getLocalPath());

        if (!pasta.isDirectory()) {
            registro.falhou(id, "A pasta do projeto não existe nesta máquina: " + pasta.getPath());
            return;
        }

        var thread = new Thread(() -> executar(id, script, pasta, variaveis, registro), "run-" + id);
        thread.setDaemon(true);
        thread.start();
    }

    private void executar(Long id, String script, File pasta, Map<String, String> variaveis,
                          Registro registro) {
        Process processo = null;
        try {
            var comando = new ProcessBuilder(interpretador(script));
            comando.directory(pasta);
            // O que diferencia homologação de produção chega por aqui: o
            // projeto de automação lê estas variáveis para escolher a URL.
            if (variaveis != null) comando.environment().putAll(variaveis);
            // Saída de erro junto com a normal: no terminal elas aparecem
            // misturadas na ordem em que aconteceram, e separá-las aqui faria a
            // falha chegar fora do contexto em que ocorreu.
            comando.redirectErrorStream(true);
            processo = comando.start();
            emAndamento.put(id, processo);

            registro.saida(id, cabecalho(script, variaveis));
            try (var leitor = new BufferedReader(
                    new InputStreamReader(processo.getInputStream(), StandardCharsets.UTF_8))) {
                String linha;
                while ((linha = leitor.readLine()) != null) {
                    registro.saida(id, linha + System.lineSeparator());
                }
            }
            registro.encerrou(id, processo.waitFor());
        } catch (InterruptedException interrompido) {
            Thread.currentThread().interrupt();
            registro.falhou(id, "Execução interrompida.");
        } catch (Exception e) {
            log.warn("Falha ao executar a execução {}: {}", id, e.toString());
            registro.falhou(id, "Não foi possível executar: " + e.getMessage());
        } finally {
            emAndamento.remove(id);
            if (processo != null && processo.isAlive()) processo.destroy();
        }
    }

    /**
     * A primeira linha do terminal mostra o comando e as variáveis usadas.
     *
     * Sem isso, duas execuções do mesmo card em ambientes diferentes produzem
     * logs idênticos no começo, e depois ninguém consegue dizer qual rodou onde.
     */
    private static String cabecalho(String script, Map<String, String> variaveis) {
        var linha = new StringBuilder();
        if (variaveis != null && !variaveis.isEmpty()) {
            linha.append("$ ");
            variaveis.forEach((chave, valor) ->
                    linha.append(chave).append("=").append(esconderSeSegredo(chave, valor)).append(" "));
            linha.append(System.lineSeparator());
        }
        return linha.append("$ ").append(script).append(System.lineSeparator()).toString();
    }

    /**
     * Valor de variável que parece credencial não vai para o log: o terminal é
     * copiado e colado em demanda do Jira o tempo todo.
     */
    private static String esconderSeSegredo(String chave, String valor) {
        String nome = chave.toUpperCase(java.util.Locale.ROOT);
        boolean sensivel = nome.contains("PASSWORD") || nome.contains("SENHA")
                || nome.contains("SECRET") || nome.contains("TOKEN") || nome.contains("KEY");
        return sensivel ? "***" : valor;
    }

    /** No Windows o comando vai pelo cmd; no resto, pelo shell. */
    private static String[] interpretador(String script) {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return windows ? new String[]{"cmd.exe", "/c", script} : new String[]{"sh", "-c", script};
    }

    @Override
    public boolean cancelar(Long execucaoId) {
        Process processo = emAndamento.remove(execucaoId);
        if (processo == null) return false;
        // Mata a árvore: o script chama o Playwright, que abre navegadores —
        // derrubar só o pai deixaria tudo isso rodando.
        processo.descendants().forEach(ProcessHandle::destroy);
        processo.destroy();
        return true;
    }
}

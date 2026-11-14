"use strict";
const { BrowserWindow, desktopCapturer, ipcMain, screen } = require("electron");
const path = require("node:path");
const pickers = new Map();

function trustedPicker(event) {
  const picker = pickers.get(event.sender.id);
  return picker && event.senderFrame === picker.window.webContents.mainFrame ? picker : null;
}
ipcMain.handle("capture-picker:list", event => {
  const picker = trustedPicker(event);
  return picker ? picker.load() : { error: "Seletor indisponível." };
});
ipcMain.on("capture-picker:select", (event, id) => {
  const picker = trustedPicker(event);
  const source = typeof id === "string" && picker?.sources.get(id);
  if (source) picker.finish(source);
});
ipcMain.on("capture-picker:cancel", event => trustedPicker(event)?.finish(null));

function chooseScreenCapture(parent, { audioRequested = false, log = () => {} } = {}) {
  return new Promise((resolve, reject) => {
    const win = new BrowserWindow({
      parent, modal: true, show: false, width: 920, height: 700,
      minWidth: 680, minHeight: 520, backgroundColor: "#17181d",
      title: "Escolher o que gravar · QA Manager", autoHideMenuBar: true,
      minimizable: false, maximizable: false,
      webPreferences: {
        preload: path.join(__dirname, "capture-picker-preload.js"),
        contextIsolation: true, nodeIntegration: false, sandbox: true,
        partition: "capture-picker", backgroundThrottling: false,
      },
    });
    const id = win.webContents.id;
    let settled = false;
    let loading;
    const state = {
      window: win, sources: new Map(),
      finish(source, error) {
        if (settled) return;
        settled = true; pickers.delete(id);
        if (!win.isDestroyed()) win.destroy();
        if (error) reject(error); else resolve(source);
      },
      load() {
        if (loading) return loading;
        loading = (async () => {
          try {
            const sources = await desktopCapturer.getSources({
              types: ["screen", "window"], thumbnailSize: { width: 480, height: 270 }, fetchWindowIcons: true,
            });
            if (settled || win.isDestroyed()) return { error: "Seletor fechado." };
            const ownId = win.getMediaSourceId();
            const displays = screen.getAllDisplays();
            const primary = screen.getPrimaryDisplay().id;
            const available = sources.filter(source => source.id !== ownId);
            state.sources = new Map(available.map(source => [source.id, source]));
            let monitor = 0;
            const previews = available.map(source => {
              const isScreen = source.id.startsWith("screen:");
              const display = isScreen && displays.find(item => String(item.id) === source.display_id);
              const name = isScreen ? "Monitor " + (++monitor) + (display && display.id === primary ? " · principal" : "") : source.name;
              const detail = display ? Math.round(display.size.width * display.scaleFactor) + " × " + Math.round(display.size.height * display.scaleFactor) : isScreen ? "Tela inteira" : "Somente esta janela";
              return { id: source.id, kind: isScreen ? "screen" : "window", name, detail,
                thumbnail: source.thumbnail.isEmpty() ? "" : source.thumbnail.toDataURL(),
                icon: source.appIcon && !source.appIcon.isEmpty() ? source.appIcon.toDataURL() : "" };
            });
            return { sources: previews, audioRequested };
          } catch (error) {
            log("[capture] Falha ao listar fontes: " + error.message);
            return { error: "Não foi possível carregar as telas e janelas. Tente atualizar a lista." };
          } finally { loading = null; }
        })();
        return loading;
      },
    };
    pickers.set(id, state);
    win.on("closed", () => state.finish(null));
    win.webContents.on("will-navigate", event => event.preventDefault());
    win.webContents.setWindowOpenHandler(() => ({ action: "deny" }));
    win.once("ready-to-show", () => { if (!settled) win.show(); });
    win.loadFile(path.join(__dirname, "capture-picker.html")).catch(error => state.finish(null, error));
  });
}
module.exports = { chooseScreenCapture };

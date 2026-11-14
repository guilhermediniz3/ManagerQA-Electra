"use strict";
const { chooseScreenCapture } = require("./capture-picker");

function sameOrigin(actual, expected) {
  try {
    const origin = new URL(actual);
    return (origin.protocol === "http:" || origin.protocol === "https:") &&
      origin.origin === new URL(expected).origin;
  } catch { return false; }
}

// A source is granted only after an explicit choice in the native picker.
function registerScreenCapture(window, getOrigin, log = () => {}) {
  const session = window.webContents.session;
  let choosing = false;
  session.setDisplayMediaRequestHandler(async (request, callback) => {
    // Electron 33 throws after rejecting an empty video response.
    const respond = (streams, reason) => {
      log("[capture] " + reason);
      try { callback(streams); }
      catch (error) { log("[capture] Resposta: " + error.message); }
    };
    if (choosing || !request.userGesture || !request.videoRequested ||
        window.isDestroyed() || request.frame !== window.webContents.mainFrame ||
        !sameOrigin(request.securityOrigin, getOrigin())) {
      respond({}, "Solicitação recusada: captura em seleção, gesto, vídeo, janela ou origem inválidos."); return;
    }
    choosing = true;
    try {
      const selected = await chooseScreenCapture(window, { audioRequested: request.audioRequested, log });
      if (!selected) { respond({}, "Seleção cancelada."); return; }
      if (window.isDestroyed() || request.frame !== window.webContents.mainFrame ||
          !sameOrigin(request.securityOrigin, getOrigin())) {
        respond({}, "Solicitação recusada: janela ou origem mudou durante a seleção."); return;
      }
      respond({video: selected, ...(request.audioRequested ? {audio: "loopback"} : {})},
        "Fonte autorizada: " + (selected.id?.startsWith("screen:") ? "tela" : "janela") + "; áudio=" + Boolean(request.audioRequested));
    } catch (error) { respond({}, "Falha ao selecionar captura: " + error.message); }
    finally { choosing = false; }
  });
}
module.exports = { registerScreenCapture };

"use strict";
const { contextBridge, ipcRenderer } = require("electron");
contextBridge.exposeInMainWorld("capturePicker", {
  list: () => ipcRenderer.invoke("capture-picker:list"),
  select: id => ipcRenderer.send("capture-picker:select", id),
  cancel: () => ipcRenderer.send("capture-picker:cancel"),
});

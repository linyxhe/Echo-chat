const { contextBridge } = require("electron");

contextBridge.exposeInMainWorld("ECHO_DESKTOP", {
  isElectron: true,
});

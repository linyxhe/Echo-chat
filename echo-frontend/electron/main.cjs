const { app, BrowserWindow, shell } = require("electron");
const path = require("path");

const createMainWindow = async () => {
  const win = new BrowserWindow({
    width: 1200,
    height: 800,
    icon: app.isPackaged
      ? path.join(process.resourcesPath, "app.asar", "favicon.ico")
      : path.join(__dirname, "..", "public", "favicon.ico"),
    webPreferences: {
      preload: path.join(__dirname, "preload.cjs"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
    },
  });

  win.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: "deny" };
  });

  if (app.isPackaged) {
    // The packaged client must load the HTTPS deployment URL. Loading bundled
    // file:// assets bypasses the production runtime config and can fall back
    // to development HTTP addresses for uploads and WebSocket calls.
    const runtimeConfigPath = path.join(__dirname, "..", "dist", "config.js");
    const runtimeConfig = require("fs").readFileSync(runtimeConfigPath, "utf8");
    const match = runtimeConfig.match(/API_BASE:\s*["'](https:\/\/[^"']+)["']/);
    if (!match) throw new Error("Packaged client requires an HTTPS API_BASE in dist/config.js");
    await win.loadURL(`${match[1].replace(/\/$/, "")}/`);
  } else {
    await win.loadURL("http://localhost:8089");
  }
};

app.whenReady().then(async () => {
  await createMainWindow();

  app.on("activate", async () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      await createMainWindow();
    }
  });
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});

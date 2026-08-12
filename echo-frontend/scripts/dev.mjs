import net from "node:net";
import { spawn } from "node:child_process";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const currentDir = path.dirname(fileURLToPath(import.meta.url));
const frontendDir = path.resolve(currentDir, "..");
const projectRoot = path.resolve(frontendDir, "..");
const tusdScript = path.join(projectRoot, "scripts", "start-tusd.ps1");
const applicationYml = path.join(projectRoot, "echo-backend", "src", "main", "resources", "application.yml");
const ymlText = readFileSync(applicationYml, "utf8");
const configuredMaxSize = ymlText.match(/max-size:\s*\$\{APP_FILE_MAX_SIZE:(\d+)\}/)?.[1];
const maxFileSize = process.env.APP_FILE_MAX_SIZE || configuredMaxSize || "21474836480";
const sharedEnv = { ...process.env, APP_FILE_MAX_SIZE: maxFileSize };
const children = [];

const isPortOpen = (port) => new Promise((resolve) => {
  const socket = net.createConnection({ host: "127.0.0.1", port });
  socket.once("connect", () => { socket.destroy(); resolve(true); });
  socket.once("error", () => resolve(false));
});

const stopChildren = () => {
  children.forEach((child) => {
    if (!child.killed) child.kill();
  });
};

process.on("SIGINT", () => { stopChildren(); process.exit(0); });
process.on("SIGTERM", () => { stopChildren(); process.exit(0); });

if (await isPortOpen(1080)) {
  console.log("[dev] tusd is already listening on 1080; reuse the existing service.");
} else {
  const tusd = spawn(
    "powershell.exe",
    ["-NoProfile", "-ExecutionPolicy", "Bypass", "-File", tusdScript, "-MaxFileSize", maxFileSize],
    { cwd: projectRoot, env: sharedEnv, stdio: "inherit", windowsHide: false }
  );
  children.push(tusd);
  tusd.on("exit", (code) => {
    if (code && code !== 0) console.error(`[dev] tusd exited with code ${code}.`);
  });
}

const vite = spawn(
  process.execPath,
  [path.join(frontendDir, "node_modules", "vite", "bin", "vite.js")],
  { cwd: frontendDir, env: sharedEnv, stdio: "inherit", windowsHide: false }
);
children.push(vite);
vite.on("exit", (code) => {
  stopChildren();
  process.exit(code ?? 0);
});

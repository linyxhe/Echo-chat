// 长连接在线测试：连接 WS 保持在线 duration 秒后关闭。用法: node hold.mjs <userId> <username> [durationMs]
import WebSocket from "ws";
import crypto from "crypto";
const SECRET = "echo-chat-secret-key";
const userId = Number(process.argv[2] || 1);
const username = process.argv[3] || "pyf";
const duration = Number(process.argv[4] || 15000);
const b64u = (b) => Buffer.from(b).toString("base64url");
const h = b64u(JSON.stringify({ alg: "HS256", typ: "JWT" }));
const now = Math.floor(Date.now() / 1000);
const p = b64u(JSON.stringify({ sub: username, userId, iat: now, exp: now + 86400 }));
const s = crypto.createHmac("sha256", SECRET).update(`${h}.${p}`).digest("base64url");
const ws = new WebSocket(`ws://localhost:8088/ws?token=${h}.${p}.${s}`);
ws.on("open", () => console.log("OPEN userId=" + userId));
ws.on("message", (r) => {
  try { const m = JSON.parse(r.toString()); if (m.type === "USER_ONLINE") console.log("broadcast USER_ONLINE:", JSON.stringify(m.data)); } catch {}
});
ws.on("close", () => { console.log("CLOSED"); process.exit(0); });
setTimeout(() => { try { ws.close(); } catch {} }, duration);

// AI Gateway 端到端 WS 测试客户端。
// 用法: node client.mjs [userId] [username] ["问题"]
import WebSocket from "ws";
import crypto from "crypto";

const SECRET = "echo-chat-secret-key"; // application-local.yml 的 jwt.secret
const API_BASE = process.env.AI_API_BASE || "http://localhost:8088";
const WS_URL = (API_BASE.replace(/^http/, "ws")) + "/ws";

const userId = Number(process.argv[2] || 1);
const username = process.argv[3] || "pyf";
const question = process.argv[4] || "你好，介绍一下你自己（一句话即可）";
const messageType = process.argv[6] || "TEXT"; // 可选：TEXT/FILE/IMAGE

const b64u = (b) => Buffer.from(b).toString("base64url");
const mint = () => {
  const h = b64u(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const now = Math.floor(Date.now() / 1000);
  const p = b64u(JSON.stringify({ sub: username, userId, iat: now, exp: now + 86400 }));
  const s = crypto.createHmac("sha256", SECRET).update(`${h}.${p}`).digest("base64url");
  return `${h}.${p}.${s}`;
};
const token = mint();

// 先从 /ai/bot-info 获取真实 bot id
const botInfo = await fetch(`${API_BASE}/ai/bot-info`, {
  headers: { Authorization: `Bearer ${token}` },
}).then((r) => r.json());
const botId = botInfo?.data?.botUserId;
if (!botId) {
  console.error("无法获取 AI bot id:", JSON.stringify(botInfo));
  process.exit(1);
}
console.log(`[bot-info] botUserId=${botId} nickname=${botInfo.data?.botNickname}`);

const ws = new WebSocket(`${WS_URL}?token=${token}`);
// 可选第 5 参为固定 clientMessageId（幂等测试用）
const clientMessageId = process.argv[5] || String(Date.now());

ws.on("open", () => {
  console.log("[open] sending CHAT_MESSAGE, clientMessageId=" + clientMessageId);
  const content =
    messageType === "FILE"
      ? JSON.stringify({ url: "legacy/test.bin", name: "test.bin", size: 3 })
      : question;
  ws.send(JSON.stringify({
    type: "CHAT_MESSAGE",
    data: { receiverId: botId, messageType, content, clientMessageId },
  }));
});

ws.on("message", (raw) => {
  let msg;
  try { msg = JSON.parse(raw.toString()); } catch { return; }
  if (msg.type === "MESSAGE_ACK") {
    console.log("[ACK]", JSON.stringify(msg.data));
  } else if (msg.type === "AI_STREAM_CHUNK") {
    process.stdout.write(msg.data.delta);
  } else if (msg.type === "AI_STREAM_DONE") {
    console.log("\n[DONE]", JSON.stringify(msg.data.message));
    ws.close();
  } else if (msg.type === "AI_STREAM_ERROR") {
    console.error("\n[ERROR]", JSON.stringify(msg.data));
    ws.close();
  }
});

ws.on("error", (e) => { console.error("ws error:", e.message); process.exit(1); });
setTimeout(() => { console.error("\n[timeout] 120s no DONE"); process.exit(2); }, 120000).unref();

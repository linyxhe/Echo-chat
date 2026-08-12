// 多标签页路由 + read_at 验证：两个 pyf 会话都应收到 linyxhe 的消息；已读回执应带 readAt。
// 用法: node multitab.mjs [port]
import WebSocket from "ws";
import crypto from "crypto";
const SECRET = "echo-chat-secret-key";
const PORT = process.argv[2] || 8093;
const base = `ws://localhost:${PORT}/ws`;
const b64u = (b) => Buffer.from(b).toString("base64url");
const mint = (uid, un) => {
  const h = b64u(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const n = Math.floor(Date.now() / 1000);
  const p = b64u(JSON.stringify({ sub: un, userId: uid, iat: n, exp: n + 86400 }));
  const s = crypto.createHmac("sha256", SECRET).update(`${h}.${p}`).digest("base64url");
  return `${h}.${p}.${s}`;
};
const mk = (uid, un) => new Promise((res, rej) => {
  const ws = new WebSocket(`${base}?token=${mint(uid, un)}`);
  ws.on("open", () => res(ws));
  ws.on("error", rej);
});
const waitType = (ws, type, timeout = 12000) => new Promise((res, rej) => {
  const h = (raw) => {
    let m; try { m = JSON.parse(raw.toString()); } catch { return; }
    if (m.type === type) { clearTimeout(t); ws.off("message", h); res(m); }
  };
  const t = setTimeout(() => { ws.off("message", h); rej(new Error("timeout " + type)); }, timeout);
  ws.on("message", h);
});

const tabA = await mk(1, "pyf");
const tabB = await mk(1, "pyf");
const sender = await mk(2, "linyxhe");

const aGot = waitType(tabA, "NEW_MESSAGE");
const bGot = waitType(tabB, "NEW_MESSAGE");

sender.send(JSON.stringify({
  type: "CHAT_MESSAGE",
  data: { receiverId: 1, messageType: "TEXT", content: "多标签页测试 " + Date.now(), clientMessageId: "mt-" + Date.now() },
}));

const [aMsg, bMsg] = await Promise.all([aGot, bGot]);
console.log("tabA got NEW_MESSAGE id:", aMsg.data.id);
console.log("tabB got NEW_MESSAGE id:", bMsg.data.id);
console.log("MULTI_TAB:", aMsg.data.id === bMsg.data.id && aMsg.data.id != null ? "PASS" : "FAIL");

const receipt = waitType(sender, "MESSAGE_READ_RECEIPT");
tabA.send(JSON.stringify({ type: "MESSAGE_READ", data: { senderId: 2, messageIds: [aMsg.data.id] } }));
const rr = await receipt;
console.log("linyxhe got MESSAGE_READ_RECEIPT:", JSON.stringify(rr.data));
console.log("READ_RECEIPT_HAS_READAT:", rr.data && rr.data.readAt ? "PASS" : "FAIL");

tabA.close(); tabB.close(); sender.close();
process.exit(0);

// 已读回执协议测试：A(pyf) 发消息给 B(linyxhe)，B 读后 A 收到 MESSAGE_READ_RECEIPT。
import WebSocket from "ws";
import crypto from "crypto";
const SECRET = "echo-chat-secret-key";
const b64u = (b) => Buffer.from(b).toString("base64url");
const mint = (uid, un) => {
  const h = b64u(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const n = Math.floor(Date.now() / 1000);
  const p = b64u(JSON.stringify({ sub: un, userId: uid, iat: n, exp: n + 86400 }));
  const s = crypto.createHmac("sha256", SECRET).update(`${h}.${p}`).digest("base64url");
  return `${h}.${p}.${s}`;
};
const mk = (uid, un) => new Promise((res, rej) => {
  const ws = new WebSocket(`ws://localhost:8088/ws?token=${mint(uid, un)}`);
  ws.on("open", () => res(ws));
  ws.on("error", rej);
});
const waitFor = (ws, type, timeout = 12000) => new Promise((res, rej) => {
  const h = (raw) => {
    let m; try { m = JSON.parse(raw.toString()); } catch { return; }
    if (m.type === type) { clearTimeout(t); ws.off("message", h); res(m); }
  };
  const t = setTimeout(() => { ws.off("message", h); rej(new Error("timeout waiting " + type)); }, timeout);
  ws.on("message", h);
});

const A = await mk(1, "pyf");
const B = await mk(2, "linyxhe");
const clientMessageId = String(Date.now());

A.send(JSON.stringify({ type: "CHAT_MESSAGE", data: { receiverId: 2, messageType: "TEXT", content: "已读回执测试", clientMessageId } }));
const ack = await waitFor(A, "MESSAGE_ACK");
console.log("[A] ACK:", JSON.stringify(ack.data));

const nm = await waitFor(B, "NEW_MESSAGE");
console.log("[B] NEW_MESSAGE id:", nm.data.id, "senderId:", nm.data.senderId);

B.send(JSON.stringify({ type: "MESSAGE_READ", data: { senderId: 1, messageIds: [nm.data.id] } }));
const rr = await waitFor(A, "MESSAGE_READ_RECEIPT");
console.log("[A] MESSAGE_READ_RECEIPT:", JSON.stringify(rr.data));

A.close(); B.close();
console.log("READ_RECEIPT_TEST_PASS");
process.exit(0);

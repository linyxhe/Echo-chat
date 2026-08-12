// 消息可靠性 v3 验证：隐私开关（默认关）+ 跨实例 pub/sub 推送。
// 用法: node v3-test.mjs [portA=8095] [portB=8096]
import WebSocket from "ws";
import crypto from "crypto";
const SECRET = "echo-chat-secret-key";
const A = Number(process.argv[2] || 8095);
const B = Number(process.argv[3] || 8096);
const b64u = (b) => Buffer.from(b).toString("base64url");
const mint = (uid, un) => {
  const h = b64u(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const n = Math.floor(Date.now() / 1000);
  const p = b64u(JSON.stringify({ sub: un, userId: uid, iat: n, exp: n + 86400 }));
  const s = crypto.createHmac("sha256", SECRET).update(`${h}.${p}`).digest("base64url");
  return `${h}.${p}.${s}`;
};
const mk = (port, uid, un) => new Promise((res, rej) => {
  const ws = new WebSocket(`ws://localhost:${port}/ws?token=${mint(uid, un)}`);
  ws.on("open", () => res(ws));
  ws.on("error", rej);
});
const rest = async (port, uid, un, method, path, body) => {
  const res = await fetch(`http://localhost:${port}${path}`, {
    method,
    headers: { Authorization: `Bearer ${mint(uid, un)}`, "Content-Type": "application/json" },
    body: body ? JSON.stringify(body) : undefined,
  });
  return res.json();
};
const waitNewMessage = (ws, timeout = 8000) => new Promise((res, rej) => {
  const h = (raw) => {
    let m; try { m = JSON.parse(raw.toString()); } catch { return; }
    if (m.type === "NEW_MESSAGE") { clearTimeout(t); ws.off("message", h); res(m); }
  };
  const t = setTimeout(() => { ws.off("message", h); rej(new Error("timeout NEW_MESSAGE")); }, timeout);
  ws.on("message", h);
});
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const out = [];

// ===== 隐私：在线状态（默认关） =====
const pyfA = await mk(A, 1, "pyf");
const linA = await mk(A, 2, "linyxhe");
await sleep(1500);

let fl = (await rest(A, 2, "linyxhe", "GET", "/friends/list")).data.list || [];
out.push(["pyf online default(off)", fl.find(f => String(f.friendId) === "1")?.online === false ? "PASS" : "FAIL"]);

await rest(A, 1, "pyf", "PUT", "/user/profile", { showOnlineStatus: true });
await sleep(500);
fl = (await rest(A, 2, "linyxhe", "GET", "/friends/list")).data.list || [];
out.push(["pyf online after enable", fl.find(f => String(f.friendId) === "1")?.online === true ? "PASS" : "FAIL"]);

// ===== 隐私：已读回执（默认关） =====
let linGotReceipt = false;
linA.on("message", (raw) => {
  let m; try { m = JSON.parse(raw.toString()); } catch { return; }
  if (m.type === "MESSAGE_READ_RECEIPT") linGotReceipt = true;
});

linA.send(JSON.stringify({ type: "CHAT_MESSAGE", data: { receiverId: 1, messageType: "TEXT", content: "隐私回执1", clientMessageId: "p1-" + Date.now() } }));
const m1 = await waitNewMessage(pyfA);
pyfA.send(JSON.stringify({ type: "MESSAGE_READ", data: { senderId: 2, messageIds: [m1.data.id] } }));
await sleep(1500);
out.push(["read receipt default(off)", linGotReceipt === false ? "PASS" : "FAIL"]);

await rest(A, 1, "pyf", "PUT", "/user/profile", { showReadReceipts: true });
await sleep(500);
linGotReceipt = false;
linA.send(JSON.stringify({ type: "CHAT_MESSAGE", data: { receiverId: 1, messageType: "TEXT", content: "隐私回执2", clientMessageId: "p2-" + Date.now() } }));
const m2 = await waitNewMessage(pyfA);
pyfA.send(JSON.stringify({ type: "MESSAGE_READ", data: { senderId: 2, messageIds: [m2.data.id] } }));
await sleep(1500);
out.push(["read receipt after enable", linGotReceipt === true ? "PASS" : "FAIL"]);

pyfA.close(); linA.close();

// ===== 跨实例 pub/sub =====
const pyfX = await mk(A, 1, "pyf");
const linX = await mk(B, 2, "linyxhe");
await sleep(1000);

const cross1 = waitNewMessage(pyfX);
linX.send(JSON.stringify({ type: "CHAT_MESSAGE", data: { receiverId: 1, messageType: "TEXT", content: "跨实例 B->A", clientMessageId: "x1-" + Date.now() } }));
const cm1 = await cross1;
out.push(["cross-instance B->A", cm1.data.senderId === 2 ? "PASS" : "FAIL"]);

const cross2 = waitNewMessage(linX);
pyfX.send(JSON.stringify({ type: "CHAT_MESSAGE", data: { receiverId: 2, messageType: "TEXT", content: "跨实例 A->B", clientMessageId: "x2-" + Date.now() } }));
const cm2 = await cross2;
out.push(["cross-instance A->B", cm2.data.senderId === 1 ? "PASS" : "FAIL"]);

pyfX.close(); linX.close();
for (const [k, v] of out) console.log(k + ":", v);
process.exit(0);

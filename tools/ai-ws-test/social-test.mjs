// 社交增强验证：群聊（建群/收发/历史/未读/已读）+ 通知（好友请求/被通过）。
// 用法: node social-test.mjs [port=8097]
import WebSocket from "ws";
import crypto from "crypto";
const SECRET = "echo-chat-secret-key";
const PORT = Number(process.argv[2] || 8097);
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
const rest = async (uid, un, method, path, body) => {
  const res = await fetch(`http://localhost:${PORT}${path}`, {
    method,
    headers: { Authorization: `Bearer ${mint(uid, un)}`, "Content-Type": "application/json" },
    body: body ? JSON.stringify(body) : undefined,
  });
  return res.json();
};
const waitFrame = (ws, type, timeout = 8000) => new Promise((res, rej) => {
  const h = (raw) => {
    let m; try { m = JSON.parse(raw.toString()); } catch { return; }
    if (m.type === type) { clearTimeout(t); ws.off("message", h); res(m); }
  };
  const t = setTimeout(() => { ws.off("message", h); rej(new Error("timeout " + type)); }, timeout);
  ws.on("message", h);
});
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const out = [];

// ==== 群聊 ====
const pyf = await mk(1, "pyf");
const lin = await mk(2, "linyxhe");
await sleep(1000);

const createRes = await rest(1, "pyf", "POST", "/groups", { name: "测试群", memberIds: [2] });
const groupId = createRes.data && createRes.data.groupId;
out.push(["create group", groupId ? "PASS" : "FAIL"]);

const linGot = waitFrame(lin, "GROUP_MESSAGE");
pyf.send(JSON.stringify({ type: "GROUP_MESSAGE", data: { groupId, messageType: "TEXT", content: "群消息测试 123", clientMessageId: "gm-" + Date.now() } }));
const gm = await linGot;
out.push(["group msg delivery", gm.data.groupId === groupId && gm.data.senderId === 1 ? "PASS" : "FAIL"]);

const pyfGroups = (await rest(1, "pyf", "GET", "/groups")).data;
const linGroups = (await rest(2, "linyxhe", "GET", "/groups")).data;
out.push(["pyf sees group", pyfGroups.some((g) => g.groupId === groupId) ? "PASS" : "FAIL"]);
const linGroup = linGroups.find((g) => g.groupId === groupId);
out.push(["linyxhe sees group", !!linGroup ? "PASS" : "FAIL"]);
out.push(["linyxhe unread >0", (linGroup && linGroup.unreadCount) > 0 ? "PASS" : "FAIL(" + (linGroup && linGroup.unreadCount) + ")"]);

await rest(2, "linyxhe", "PUT", `/groups/${groupId}/read`);
const linGroups2 = (await rest(2, "linyxhe", "GET", "/groups")).data;
const linGroup2 = linGroups2.find((g) => g.groupId === groupId);
out.push(["linyxhe unread after read =0", (linGroup2 && linGroup2.unreadCount) === 0 ? "PASS" : "FAIL"]);

const hist = (await rest(1, "pyf", "GET", `/groups/${groupId}/messages`)).data.messages;
out.push(["group history", hist.some((m) => m.content === "群消息测试 123") ? "PASS" : "FAIL"]);

// ==== 通知 ====
const admin = await mk(3, "admin");
await sleep(500);

const adminGotNotif = waitFrame(admin, "NOTIFICATION");
await rest(1, "pyf", "POST", "/friends/request", { targetUserId: 3, remark: "我是pyf" });
const n1 = await adminGotNotif;
out.push(["admin got FRIEND_REQUEST notif", n1.data && n1.data.type === "FRIEND_REQUEST" ? "PASS" : "FAIL"]);

const cnt = (await rest(3, "admin", "GET", "/notifications/unread-count")).data.unreadCount;
out.push(["admin unread count >0", cnt > 0 ? "PASS" : "FAIL(" + cnt + ")"]);

const pyfGotNotif = waitFrame(pyf, "NOTIFICATION");
const reqs = (await rest(3, "admin", "GET", "/friends/requests")).data.list;
const pendingReq = reqs.find((r) => r.senderId === 1 && r.status === "PENDING");
await rest(3, "admin", "PUT", `/friends/request/${pendingReq.id}/handle`, { action: "ACCEPT", remark: "备注" });
const n2 = await pyfGotNotif;
out.push(["pyf got ACCEPT notif", n2.data && n2.data.type === "FRIEND_REQUEST_ACCEPTED" ? "PASS" : "FAIL"]);

pyf.close(); lin.close(); admin.close();
for (const [k, v] of out) console.log(k + ":", v);
process.exit(0);

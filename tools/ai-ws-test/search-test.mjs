// 全文搜索验证：用户 + 1:1 消息 + 群消息。用法: node search-test.mjs [port=8098]
import WebSocket from "ws";
import crypto from "crypto";
const SECRET = "echo-chat-secret-key";
const PORT = Number(process.argv[2] || 8098);
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
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const out = [];

// 造数据：pyf 发 1:1 消息 + 建群发群消息
const pyf = await mk(1, "pyf");
const lin = await mk(2, "linyxhe");
await sleep(1000);

pyf.send(JSON.stringify({ type: "CHAT_MESSAGE", data: { receiverId: 2, messageType: "TEXT", content: "这条消息含独特搜索词 SEARCHX1", clientMessageId: "s1-" + Date.now() } }));
await sleep(500);

const grp = (await rest(1, "pyf", "POST", "/groups", { name: "搜索测试群", memberIds: [2] })).data;
await sleep(500);
pyf.send(JSON.stringify({ type: "GROUP_MESSAGE", data: { groupId: grp.groupId, messageType: "TEXT", content: "群里的独特搜索词 SEARCHX2", clientMessageId: "s2-" + Date.now() } }));
await sleep(1000);

// 1) 搜用户
const rUsers = (await rest(1, "pyf", "GET", "/search?keyword=lin&limit=10")).data;
out.push(["user search 'lin' hits linyxhe", rUsers.users.some((u) => u.username === "linyxhe") ? "PASS" : "FAIL"]);
out.push(["user isFriend flag", rUsers.users.find((u) => u.username === "linyxhe")?.isFriend === true ? "PASS" : "FAIL"]);

// 2) 搜 1:1 消息
const rMsg = (await rest(1, "pyf", "GET", "/search?keyword=SEARCHX1&limit=10")).data;
out.push(["1:1 message search", rMsg.friendMessages.some((m) => m.content.includes("SEARCHX1") && m.friendId === 2) ? "PASS" : "FAIL"]);

// 3) 搜群消息
const rGrp = (await rest(1, "pyf", "GET", "/search?keyword=SEARCHX2&limit=10")).data;
out.push(["group message search", rGrp.groupMessages.some((m) => m.content.includes("SEARCHX2") && m.groupId === grp.groupId) ? "PASS" : "FAIL"]);

// 4) 空关键字 → 三组空
const rEmpty = (await rest(1, "pyf", "GET", "/search?keyword=&limit=10")).data;
out.push(["empty keyword -> empty", !rEmpty.users.length && !rEmpty.friendMessages.length && !rEmpty.groupMessages.length ? "PASS" : "FAIL"]);

pyf.close(); lin.close();
for (const [k, v] of out) console.log(k + ":", v);
process.exit(0);

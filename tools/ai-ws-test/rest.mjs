// 通用 REST 测试助手：mint JWT 后调用后端接口。
// 用法: MSYS_NO_PATHCONV=1 node rest.mjs <userId> <username> [method] [path] [jsonBody?]
import crypto from "crypto";
const SECRET = "echo-chat-secret-key";
const API_BASE = process.env.AI_API_BASE || "http://localhost:8088";
const [userId = "1", username = "pyf", method = "GET", path = "/ai/bot-info", body] = process.argv.slice(2);
const b64u = (b) => Buffer.from(b).toString("base64url");
const h = b64u(JSON.stringify({ alg: "HS256", typ: "JWT" }));
const now = Math.floor(Date.now() / 1000);
const p = b64u(JSON.stringify({ sub: username, userId: Number(userId), iat: now, exp: now + 86400 }));
const s = crypto.createHmac("sha256", SECRET).update(`${h}.${p}`).digest("base64url");
const token = `${h}.${p}.${s}`;
const url = API_BASE + (path.startsWith("/") ? path : "/" + path);
const res = await fetch(url, {
  method,
  headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
  body: body ? JSON.stringify(JSON.parse(body)) : undefined,
});
console.log(`${method} ${path} ->`, await res.text());

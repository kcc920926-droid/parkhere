/* drive.olvend.com 정적 서버. 릴리스의 site/ 폴더를 시작할 때 한 번 읽어 메모리에서 내준다.
   하위 폴더(assets/)와 이미지·글꼴을 지원한다. 사용: node origin.cjs <release/site> [port] */
const http = require("node:http");
const fs = require("node:fs");
const path = require("node:path");

const [root, portText = "8769"] = process.argv.slice(2);
const port = Number(portText);
if (!root || !Number.isInteger(port) || port < 0 || port > 65535) {
  throw new Error("Usage: node origin.cjs <release/site> [port]");
}

const types = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".md": "text/plain; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".webp": "image/webp",
  ".jpg": "image/jpeg",
  ".ico": "image/x-icon",
  ".woff2": "font/woff2",
};
const files = new Map();
const load = (dir, prefix) => {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name.startsWith(".")) continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) { load(full, prefix + entry.name + "/"); continue; }
    const type = types[path.extname(entry.name).toLowerCase()];
    if (entry.isFile() && type && !entry.name.endsWith(".test.js")) {
      files.set(prefix + entry.name, { type, body: fs.readFileSync(full) });
    }
  }
};
load(root, "/");
if (!files.has("/index.html")) throw new Error("No landing page in release");

const server = http.createServer((request, response) => {
  response.setHeader("X-Content-Type-Options", "nosniff");
  response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
  if (request.method !== "GET" && request.method !== "HEAD") {
    response.writeHead(405, { Allow: "GET, HEAD" }).end();
    return;
  }
  let url;
  try { url = decodeURIComponent(request.url.split("?")[0]); } catch { url = ""; }
  const file = files.get(url === "/" ? "/index.html" : url);
  if (!file) {
    response.writeHead(404, { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "no-store" }).end("Not found");
    return;
  }
  const cache = url.startsWith("/assets/") ? "public, max-age=3600" : "no-cache";
  response.writeHead(200, { "Content-Type": file.type, "Content-Length": file.body.length, "Cache-Control": cache });
  response.end(request.method === "HEAD" ? undefined : file.body);
});

server.listen(port, "127.0.0.1", () => console.log("Origin ready on 127.0.0.1:" + server.address().port));

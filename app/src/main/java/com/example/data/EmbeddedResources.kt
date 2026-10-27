package com.example.data

object EmbeddedResources {

    const val SERVER_JS_CODE = """// RemoteLink: Secure Pairing + Relay Server
// Setup:
//   npm init -y && npm i ws
// Run (Development):
//   DEV=1 node server.js
// Run (Production):
//   PANEL_PASSWORD="your-strong-password" node server.js

const http = require("http");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const { WebSocketServer } = require("ws");

const PORT = process.env.PORT || 8080;
const CODE_TTL_MS = 2 * 60 * 1000; // 2 minutes valid pairing code
const sessions = new Map(); // code -> { panel, device, consent, createdAt }

function newCode() {
  return crypto.randomBytes(4).toString("hex").toUpperCase(); // e.g. "9F3A1C7B"
}

const server = http.createServer((req, res) => {
  // Serve the web panel
  if (req.method === "GET" && (req.url === "/" || req.url === "/index.html")) {
    const panelPath = path.join(__dirname, "panel.html");
    if (fs.existsSync(panelPath)) {
      res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
      return fs.createReadStream(panelPath).pipe(res);
    }
  }

  // Create new pairing code for panel
  if (req.method === "POST" && req.url === "/pair/new") {
    const code = newCode();
    sessions.set(code, {
      panel: null,
      device: null,
      consent: false,
      createdAt: Date.now()
    });
    res.writeHead(200, { "Content-Type": "application/json" });
    return res.end(JSON.stringify({ code, expiresInSec: CODE_TTL_MS / 1000 }));
  }

  res.writeHead(404);
  res.end("Not Found");
});

const wss = new WebSocketServer({ server, path: "/ws" });

wss.on("connection", (ws, req) => {
  const url = new URL(req.url, "http://localhost");
  const role = url.searchParams.get("role"); // "panel" | "device"
  const code = url.searchParams.get("code");
  const s = sessions.get(code);

  if (!s || !["panel", "device"].includes(role) || Date.now() - s.createdAt > CODE_TTL_MS) {
    ws.close(4001, "Invalid or expired pairing code");
    return;
  }

  if (s[role]) {
    ws.close(4002, "Role already connected");
    return;
  }

  s[role] = ws;
  const peerOf = () => (role === "panel" ? s.device : s.panel);

  // Inform panel when device joins
  if (role === "device" && s.panel) {
    s.panel.send(JSON.stringify({ type: "device_joined" }));
  }

  ws.on("message", (data, isBinary) => {
    if (!isBinary) {
      let msg;
      try {
        msg = JSON.parse(data.toString());
      } catch {
        return;
      }

      // Explicit consent control - only DEVICE owner can grant or revoke
      if (role === "device" && msg.type === "consent") {
        s.consent = msg.granted === true;
        if (s.panel) {
          s.panel.send(JSON.stringify({ type: "consent", granted: s.consent }));
        }
        return;
      }
    }

    // Never relay data unless device owner explicitly approved
    if (!s.consent) return;

    const peer = peerOf();
    if (peer && peer.readyState === 1) {
      peer.send(data, { binary: isBinary });
    }
  });

  ws.on("close", () => {
    s[role] = null;
    s.consent = false; // Always reset consent immediately on disconnect
    const peer = peerOf();
    if (peer && peer.readyState === 1) {
      peer.send(JSON.stringify({ type: "peer_left" }));
    }
    if (!s.panel && !s.device) {
      sessions.delete(code);
    }
  });
});

// Periodic cleanup of expired stale codes
setInterval(() => {
  const now = Date.now();
  for (const [code, s] of sessions) {
    if (!s.device && now - s.createdAt > CODE_TTL_MS) {
      sessions.delete(code);
    }
  }
}, 30000);

server.listen(PORT, "0.0.0.0", () => {
  console.log(`RemoteLink server running on http://0.0.0.0:${'$'}PORT`);
  console.log(`Open http://localhost:${'$'}PORT in your browser to view the Web Panel.`);
});
"""

    const val PANEL_HTML_CODE = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>RemoteLink Panel</title>
  <script src="https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js"></script>
  <style>
    :root {
      --bg: #090D16;
      --card: #0F172A;
      --card-border: #1E293B;
      --cyan: #00D1FF;
      --indigo: #818CF8;
      --emerald: #10B981;
      --amber: #F59E0B;
      --crimson: #EF4444;
      --text: #F8FAFC;
      --text-sub: #94A3B8;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
    body { background: var(--bg); color: var(--text); padding: 24px; min-height: 100vh; }
    .container { max-width: 900px; margin: 0 auto; display: flex; flex-direction: column; gap: 20px; }
    header { display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid var(--card-border); padding-bottom: 16px; }
    h1 { font-size: 24px; color: var(--cyan); display: flex; align-items: center; gap: 10px; }
    .badge { padding: 4px 10px; border-radius: 9999px; font-size: 12px; font-weight: 600; text-transform: uppercase; }
    .badge-disconnected { background: #334155; color: #cbd5e1; }
    .badge-waiting { background: #78350f; color: var(--amber); }
    .badge-approved { background: #064e3b; color: var(--emerald); }
    .card { background: var(--card); border: 1px solid var(--card-border); border-radius: 12px; padding: 20px; }
    .card-title { font-size: 16px; font-weight: 600; margin-bottom: 12px; color: var(--cyan); display: flex; align-items: center; gap: 8px; }
    .row { display: flex; gap: 12px; flex-wrap: wrap; }
    input[type="text"], input[type="file"] { background: #1e293b; border: 1px solid #334155; color: #fff; padding: 10px 14px; border-radius: 8px; flex: 1; }
    button { background: var(--cyan); color: #000; border: none; padding: 10px 18px; border-radius: 8px; font-weight: 600; cursor: pointer; transition: 0.2s; }
    button:hover { opacity: 0.9; }
    button.btn-danger { background: var(--crimson); color: #fff; }
    #qr-container { background: #fff; padding: 14px; border-radius: 8px; width: fit-content; margin: 12px 0; }
    .progress-bar-container { width: 100%; height: 8px; background: #1e293b; border-radius: 4px; overflow: hidden; margin: 10px 0; }
    .progress-bar { height: 100%; width: 0%; background: var(--cyan); transition: width 0.1s; }
    .list { display: flex; flex-direction: column; gap: 8px; max-height: 250px; overflow-y: auto; }
    .list-item { background: #1e293b; padding: 10px 14px; border-radius: 8px; font-size: 14px; display: flex; justify-content: space-between; align-items: center; }
    .notif-item { border-left: 3px solid var(--indigo); flex-direction: column; align-items: flex-start; gap: 4px; }
    .notif-header { display: flex; justify-content: space-between; width: 100%; font-size: 12px; color: var(--text-sub); }
  </style>
</head>
<body>
  <div class="container">
    <header>
      <h1><span>🔗</span> RemoteLink Panel</h1>
      <span id="status-badge" class="badge badge-disconnected">Disconnected</span>
    </header>

    <!-- Step 1: Pairing -->
    <div class="card">
      <div class="card-title">1. Device Pairing</div>
      <p style="color: var(--text-sub); font-size: 14px; margin-bottom: 12px;">
        Generate a pairing code. Scan the QR code or enter the code on the RemoteLink Android app.
      </p>
      <div class="row">
        <input type="text" id="server-addr" placeholder="Server Host:Port (e.g. 192.168.1.5:8080)">
        <button id="btn-new-code" onclick="createPairingCode()">Generate Pairing Code</button>
      </div>
      <div id="qr-box" style="display: none; margin-top: 14px;">
        <div style="font-size: 15px;">Pairing Code: <strong id="code-text" style="color: var(--cyan); letter-spacing: 2px;"></strong></div>
        <div id="qr-container"></div>
      </div>
    </div>

    <!-- Step 2: File Transfer -->
    <div class="card">
      <div class="card-title">2. Send File to Phone</div>
      <div class="row">
        <input type="file" id="file-input">
        <button id="btn-send-file" onclick="sendFileToPhone()" disabled>Send File</button>
      </div>
      <div id="transfer-box" style="display: none; margin-top: 12px;">
        <div id="transfer-info" style="font-size: 13px; color: var(--text-sub);">Transferring...</div>
        <div class="progress-bar-container"><div id="progress-bar" class="progress-bar"></div></div>
      </div>
      <div style="margin-top: 16px;">
        <div style="font-size: 14px; font-weight: 600; margin-bottom: 8px;">Received Files from Phone</div>
        <div id="received-files" class="list">
          <div style="color: var(--text-sub); font-size: 13px;">No files received yet.</div>
        </div>
      </div>
    </div>

    <!-- Step 3: Mirrored Notifications -->
    <div class="card">
      <div class="card-title">3. Mirrored Phone Notifications</div>
      <div id="notif-list" class="list">
        <div style="color: var(--text-sub); font-size: 13px;">Waiting for phone notifications...</div>
      </div>
    </div>
  </div>

  <script>
    let ws = null;
    let currentCode = null;
    let incomingFile = null;
    const CHUNK_SIZE = 64 * 1024;

    function getHost() {
      const input = document.getElementById("server-addr").value.trim();
      return input || window.location.host || "localhost:8080";
    }

    async function createPairingCode() {
      const host = getHost();
      try {
        const res = await fetch(`http://${'$'}{host}/pair/new`, { method: "POST" });
        const data = await res.json();
        currentCode = data.code;
        document.getElementById("code-text").textContent = currentCode;
        document.getElementById("qr-box").style.display = "block";

        const qrContainer = document.getElementById("qr-container");
        qrContainer.innerHTML = "";
        const qrUrl = `ws://${'$'}{host}/ws?role=device&code=${'$'}{currentCode}`;
        new QRCode(qrContainer, { text: qrUrl, width: 160, height: 160 });

        connectPanelWs(host, currentCode);
      } catch (e) {
        alert("Failed to connect to server: " + e.message);
      }
    }

    function connectPanelWs(host, code) {
      if (ws) ws.close();
      const badge = document.getElementById("status-badge");
      badge.textContent = "Connecting...";
      badge.className = "badge badge-waiting";

      ws = new WebSocket(`ws://${'$'}{host}/ws?role=panel&code=${'$'}{code}`);
      ws.binaryType = "arraybuffer";

      ws.onopen = () => {
        badge.textContent = "Waiting for Phone...";
        badge.className = "badge badge-waiting";
      };

      ws.onmessage = (event) => {
        if (typeof event.data === "string") {
          const msg = JSON.parse(event.data);
          handleControlMessage(msg);
        } else {
          handleBinaryChunk(event.data);
        }
      };

      ws.onclose = () => {
        badge.textContent = "Disconnected";
        badge.className = "badge badge-disconnected";
        document.getElementById("btn-send-file").disabled = true;
      };
    }

    function handleControlMessage(msg) {
      const badge = document.getElementById("status-badge");
      if (msg.type === "device_joined") {
        badge.textContent = "Phone Joined (Awaiting Consent)";
        badge.className = "badge badge-waiting";
      } else if (msg.type === "consent") {
        if (msg.granted) {
          badge.textContent = "Connected & Approved";
          badge.className = "badge badge-approved";
          document.getElementById("btn-send-file").disabled = false;
        } else {
          badge.textContent = "Consent Denied";
          badge.className = "badge badge-disconnected";
          document.getElementById("btn-send-file").disabled = true;
        }
      } else if (msg.type === "file_start") {
        incomingFile = {
          name: msg.name,
          size: msg.size,
          mime: msg.mime || "application/octet-stream",
          received: 0,
          chunks: []
        };
      } else if (msg.type === "file_end") {
        if (incomingFile) {
          const blob = new Blob(incomingFile.chunks, { type: incomingFile.mime });
          const url = URL.createObjectURL(blob);
          addReceivedFile(incomingFile.name, incomingFile.size, url);
          incomingFile = null;
        }
      } else if (msg.type === "notification") {
        addNotification(msg);
      }
    }

    function handleBinaryChunk(arrayBuffer) {
      if (!incomingFile) return;
      incomingFile.chunks.push(arrayBuffer);
      incomingFile.received += arrayBuffer.byteLength;
    }

    async function sendFileToPhone() {
      const fileInput = document.getElementById("file-input");
      if (!fileInput.files.length) return alert("Select a file first");
      const file = fileInput.files[0];

      if (!ws || ws.readyState !== WebSocket.OPEN) return alert("Not connected");

      ws.send(JSON.stringify({
        type: "file_start",
        name: file.name,
        size: file.size,
        mime: file.type || "application/octet-stream"
      }));

      const transferBox = document.getElementById("transfer-box");
      const progressBar = document.getElementById("progress-bar");
      const transferInfo = document.getElementById("transfer-info");
      transferBox.style.display = "block";

      let offset = 0;
      while (offset < file.size) {
        const slice = file.slice(offset, offset + CHUNK_SIZE);
        const buf = await slice.arrayBuffer();
        ws.send(buf);
        offset += buf.byteLength;
        const pct = Math.round((offset / file.size) * 100);
        progressBar.style.width = pct + "%";
        transferInfo.textContent = `Sending ${'$'}{file.name}: ${'$'}{pct}%`;
        await new Promise(r => setTimeout(r, 10)); // Flow control
      }

      ws.send(JSON.stringify({ type: "file_end", name: file.name }));
      transferInfo.textContent = `Completed sending ${'$'}{file.name}!`;
      setTimeout(() => { transferBox.style.display = "none"; }, 3000);
    }

    function addReceivedFile(name, size, url) {
      const list = document.getElementById("received-files");
      const sizeStr = (size / 1024).toFixed(1) + " KB";
      const item = document.createElement("div");
      item.className = "list-item";
      item.innerHTML = `<div><strong>${'$'}{name}</strong> (${'$'}{sizeStr})</div>
                        <a href="${'$'}{url}" download="${'$'}{name}"><button>Download</button></a>`;
      list.prepend(item);
    }

    function addNotification(notif) {
      const list = document.getElementById("notif-list");
      const time = new Date(notif.timestamp || Date.now()).toLocaleTimeString();
      const item = document.createElement("div");
      item.className = "list-item notif-item";
      item.innerHTML = `<div class="notif-header"><strong>${'$'}{notif.appName || "Phone"}</strong><span>${'$'}{time}</span></div>
                        <div style="font-weight: 600;">${'$'}{notif.title || ""}</div>
                        <div style="color: var(--text-sub);">${'$'}{notif.text || ""}</div>`;
      list.prepend(item);
    }
  </script>
</body>
</html>
"""

    const val SETUP_GUIDE_BN = """### সার্ভার চালু ও পিসি কানেকশন নির্দেশিকা:

১. **পিসিতে ফোল্ডার প্রস্তুত করুন:**
   পিসির যেকোনো ফোল্ডারে টার্মিনাল বা CMD খুলে নিচের কমান্ড চালান:
   ```bash
   npm init -y && npm i ws
   ```

২. **সার্ভার ও প্যানেল ফাইল সংরক্ষণ করুন:**
   - অ্যাপের 'Server Script' ট্যাব থেকে কোড কপি করে `server.js` নামে সেভ করুন।
   - 'Web Panel' ট্যাব থেকে কোড কপি করে `panel.html` নামে একই ফোল্ডারে সেভ করুন।

৩. **সার্ভার চালু করুন:**
   ```bash
   DEV=1 node server.js
   ```
   (Windows CMD-তে: `set DEV=1 && node server.js`)

৪. **ব্রাউজারে প্যানেল খুলুন:**
   - পিসির ব্রাউজারে `http://localhost:8080` খুলুন।
   - পিসির Wi-Fi IP দিন (যেমন: `192.168.1.5:8080`)।
   - "Generate Pairing Code" বাটনে ক্লিক করুন।

৫. **মোবাইল অ্যাপে যুক্ত হন:**
   - ফোন এবং পিসি একই Wi-Fi নেটওয়ার্কে যুক্ত থাকতে হবে।
   - অ্যাপে 'Scan QR' চাপুন অথবা Pairing Code বসিয়ে Connect চাপুন।
   - স্ক্রিনে 'Allow' সম্মতি দিন। এরপর ফাইল পাঠানো ও নোটিফিকেশন মিরর শুরু হবে!
"""

    const val SETUP_GUIDE_EN = """### Server Setup & PC Connection Guide:

1. **Prepare PC Folder:**
   Open Terminal / CMD in a new folder on your PC and run:
   ```bash
   npm init -y && npm i ws
   ```

2. **Save Server & Web Panel Files:**
   - Copy code from 'Server Script' tab and save as `server.js`.
   - Copy code from 'Web Panel' tab and save as `panel.html` in the same directory.

3. **Start the Relay Server:**
   ```bash
   DEV=1 node server.js
   ```
   (Windows CMD: `set DEV=1 && node server.js`)

4. **Open Panel in Browser:**
   - Open `http://localhost:8080` on your PC browser.
   - Enter your PC's Wi-Fi LAN IP (e.g., `192.168.1.5:8080`).
   - Click "Generate Pairing Code" to display the QR code.

5. **Connect with RemoteLink Phone App:**
   - Ensure Phone and PC are on the same Wi-Fi network.
   - Tap 'Scan QR' in the app or type in the code, then press Connect.
   - Tap 'Allow' on the consent prompt to start transferring files and mirroring notifications!
"""
}

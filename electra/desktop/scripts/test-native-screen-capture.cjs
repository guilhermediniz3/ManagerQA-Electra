"use strict";
// Real Electron getDisplayMedia + MediaRecorder; captures only this test page.
// Native dialogs are simulated so the test never selects a user's desktop.
const { app, BrowserWindow, dialog, desktopCapturer } = require("electron");
const fs = require("node:fs"), path = require("node:path"), http = require("node:http"), assert = require("node:assert/strict");
const out = path.resolve(__dirname, "../../drive-validation/capture-native");
fs.mkdirSync(out, { recursive: true });
app.setPath("userData", path.join(out, "test-profile"));
const report = { electron: process.versions.electron, dialogs: 0, logs: [], tests: [] };
const server = http.createServer((_req, res) => { res.setHeader("Content-Type", "text/html"); res.end('<!doctype html><title>Capture regression</title><body style="background:#123456;color:white"><h1>Capture regression fixture</h1><script>setInterval(()=>document.body.style.background=Date.now()%2?"#123456":"#234567",100)</script></body>'); });
const save = () => fs.writeFileSync(path.join(out, "results.json"), JSON.stringify(report, null, 2));
const timeout = setTimeout(() => { report.error = "Timed out"; save(); app.exit(1); }, 25000);
app.whenReady().then(async () => {
  try {
    await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
    const origin = "http://127.0.0.1:" + server.address().port;
    const win = new BrowserWindow({ show: false, width: 320, height: 240, webPreferences: { backgroundThrottling: false, offscreen: true } });
    const ses = win.webContents.session;
    const register = ses.setDisplayMediaRequestHandler.bind(ses);
    ses.setDisplayMediaRequestHandler = handler => register((request, callback) => {
      report.request = { securityOrigin: request.securityOrigin, expectedOrigin: origin, userGesture: request.userGesture, videoRequested: request.videoRequested, mainFrame: request.frame === win.webContents.mainFrame };
      return handler(request, callback);
    });
    let cancel = true;
    require("../capture-picker").chooseScreenCapture = async () => { report.dialogs++; return cancel ? null : win.webContents.mainFrame; };
    // WebFrameMain is a supported video source; do not enumerate real windows.
    desktopCapturer.getSources = async () => [win.webContents.mainFrame];
    require("../capture").registerScreenCapture(win, () => origin, line => report.logs.push(line));
    win.webContents.on("paint", () => {}); win.webContents.setFrameRate(20); await win.loadURL(origin); win.webContents.startPainting(); const repaint = setInterval(() => { void win.webContents.capturePage(); }, 50);
    const canceled = await win.webContents.executeJavaScript('navigator.mediaDevices.getDisplayMedia({video:true,audio:false}).then(s=>{s.getTracks().forEach(t=>t.stop());return {ok:true}}).catch(e=>({name:e.name,message:e.message}))', true);
    assert.equal(report.dialogs, 1, "Native trailing-slash origin must reach chooser");
    assert.ok(canceled.name); report.tests.push({ mode: "native-cancellation", result: canceled });
    cancel = false;
    const recorded = await win.webContents.executeJavaScript(`(async () => {
      const stream = await navigator.mediaDevices.getDisplayMedia({video:{frameRate:20},audio:false});
      try {
        const rec = new MediaRecorder(stream, {mimeType:"video/webm"});
        const chunks=[];
        const done = new Promise((resolve,reject)=>{rec.ondataavailable=e=>chunks.push(e.data);rec.onerror=e=>reject(e.error);rec.onstop=resolve;});
        rec.start(100);setTimeout(()=>rec.stop(),1500);await done;
        return {bytes:new Blob(chunks).size,videoTracks:stream.getVideoTracks().length};
      } finally { stream.getTracks().forEach(t=>t.stop()); }
    })()`, true);
    report.recorded = recorded; assert.ok(recorded.bytes > 100); assert.equal(recorded.videoTracks, 1); assert.equal(report.dialogs, 2);
    report.tests.push({ mode:"native-recording", ...recorded }); report.passed = true; save();
    clearTimeout(timeout); server.close(); app.exit(0);
  } catch (error) { report.error = error.stack; save(); app.exit(1); }
});



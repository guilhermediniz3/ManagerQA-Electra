"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");
const source = fs.readFileSync(path.join(__dirname, "../capture.js"), "utf8");
function fixture() {
  let handler;
  const frame = {};
  const state = { dialogs: 0, responses: [1, 1], logs: [], sources: [{ id: "screen:1", name: "Test screen" }], origin: "http://127.0.0.1:18081" };
  const window = { webContents: { mainFrame: frame, session: { setDisplayMediaRequestHandler: fn => { handler = fn; } } }, isDestroyed: () => false };
  const context = { URL, module: { exports: {} }, require: () => ({
    chooseScreenCapture: async () => {
      state.dialogs++;
      if (state.onDialog) await state.onDialog();
      if (state.error) throw state.error;
      return state.responses.shift() ? state.sources[0] || null : null;
    },
  }) };
  vm.runInNewContext(source, context);
  context.module.exports.registerScreenCapture(window, () => state.origin, line => state.logs.push(line));
  const request = { userGesture: true, videoRequested: true, audioRequested: false, frame, securityOrigin: state.origin + "/" };
  state.run = async (overrides = {}, callback) => {
    const replies = [];
    await handler({ ...request, ...overrides }, streams => { replies.push(streams); callback?.(streams); });
    assert.equal(replies.length, 1, "callback invoked exactly once");
    return replies[0];
  };
  return state;
}
test("Electron origin with trailing slash reaches selection and grants chosen video", async () => {
  const f = fixture(); const result = await f.run();
  assert.equal(result.video.id, "screen:1"); assert.equal(f.dialogs, 1); assert.equal(result.audio, undefined);
});
test("origin without trailing slash and optional audio remain supported", async () => {
  const f = fixture(); const result = await f.run({ securityOrigin: f.origin, audioRequested: true });
  assert.equal(result.video.id, "screen:1"); assert.equal(result.audio, "loopback");
});
test("foreign origins, ports, frames, missing gesture and video remain blocked", async () => {
  for (const overrides of [{securityOrigin:"http://127.0.0.1:18082/"},{securityOrigin:"https://evil.test/"},{securityOrigin:"http://127.0.0.1.evil.test:18081/"},{securityOrigin:"file:///"},{securityOrigin:"invalid"},{frame:{}},{userGesture:false},{videoRequested:false}]) {
    const f = fixture(); assert.equal(Object.keys(await f.run(overrides)).length, 0); assert.equal(f.dialogs, 0);
  }
});
test("cancel, empty sources and enumeration failure are logged and reject once", async () => {
  for (const mode of ["cancel", "empty", "error"]) {
    const f = fixture();
    if (mode === "cancel") f.responses = [0];
    if (mode === "empty") f.sources = [];
    if (mode === "error") f.error = new Error("capture unavailable");
    assert.equal(Object.keys(await f.run()).length, 0); assert.ok(f.logs.length);
  }
});
test("Electron rejection exceptions do not escape or invoke the callback twice", async () => {
  const f = fixture(); f.responses = [0];
  await f.run({}, () => { throw new TypeError("Video was requested, but no video stream was provided"); });
  assert.ok(f.logs.some(line => line.includes("Video was requested")));
  f.responses = [1, 1]; assert.equal((await f.run()).video.id, "screen:1");
});
test("origin is rechecked after selection", async () => {
  const f = fixture(); f.onDialog = () => { f.origin = "http://127.0.0.1:18082"; };
  assert.equal(Object.keys(await f.run()).length, 0);
});
test("concurrent requests cannot open a second chooser", async () => {
  const f = fixture(); let release, entered;
  const ready = new Promise(resolve => { entered = resolve; });
  f.onDialog = () => { if (f.dialogs === 1) return new Promise(resolve => { release = resolve; entered(); }); };
  const pending = f.run(); await ready;
  assert.equal(Object.keys(await f.run()).length, 0); assert.equal(f.dialogs, 1);
  release(); assert.equal((await pending).video.id, "screen:1");
});


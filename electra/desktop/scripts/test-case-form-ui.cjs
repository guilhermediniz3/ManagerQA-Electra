const { app, BrowserWindow } = require("electron");
const fs = require("fs"), path = require("path"), http = require("http"), vm = require("vm");
const base = path.resolve(__dirname, "../..");
const out = path.join(base, "drive-validation");
fs.mkdirSync(out, {recursive: true});
app.setPath("userData", path.join(out, "electron-profile"));
const fixture = String.raw`
import React from "react";
import { createRoot } from "react-dom/client";
import { MantineProvider } from "@mantine/core";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { TestCaseForm } from "./src/features/testCases/TestCaseForm";
import { ScenarioRecorder } from "./src/features/testCases/ScenarioRecorder";
import { GoogleDriveCard } from "./src/features/integrations/GoogleDriveCard";
import { videoEvidence } from "./src/utils/videoEvidence";
import { montarPlayers } from "./src/features/doc/DocContent";
import "@mantine/core/styles.css";
const container = document.createElement("div"); document.body.appendChild(container);
let root = createRoot(container);
const canvas = document.createElement("canvas"); canvas.width = 320; canvas.height = 180;
document.body.appendChild(canvas); canvas.style.cssText="position:fixed;bottom:0;left:0;width:160px;height:90px";
const ctx = canvas.getContext("2d")!;
setInterval(() => { ctx.fillStyle = "#123456"; ctx.fillRect(0,0,320,180); ctx.fillStyle = "white"; ctx.font = "20px sans-serif"; ctx.fillText("Cenário sintético " + Date.now(),10,90); (window as any).capture?.getVideoTracks()[0]?.requestFrame?.(); }, 60);
Object.defineProperty(window, "isSecureContext", {get: () => (window as any).fixture?.mode !== "insecure"});
Object.defineProperty(navigator.mediaDevices, "getDisplayMedia", {value: async (options: DisplayMediaStreamOptions) => {
  (window as any).fixture.options = options;
  if ((window as any).fixture.mode === "cancel") throw new DOMException("cancelled", "NotAllowedError");
  if ((window as any).fixture.mode === "abort") throw new DOMException("Error starting capture", "AbortError");
  if ((window as any).fixture.mode === "unreadable") throw new DOMException("Cannot start", "NotReadableError");
  const stream = canvas.captureStream(20); (window as any).capture = stream; return stream;
}});
(window as any).mount = (mode: string, settings = false) => {
  root.unmount(); root = createRoot(container);
  (window as any).fixture = {mode, uploads: 0, saves: 0, saved: "", sizes: [], ready: mode !== "unconfigured"};
  root.render(<MantineProvider>{settings ? <GoogleDriveCard /> : <ScenarioRecorder caseId={42} scenario="Validar login com vídeo de evidência" previousUrl="https://vimeo.com/123" onSaved={url => {(window as any).fixture.saved = url;}} onClose={() => root.unmount()} />}</MantineProvider>);
};
(window as any).mountForm = () => {
  root.unmount(); root=createRoot(container);
  root.render(<MantineProvider><QueryClientProvider client={new QueryClient()}><TestCaseForm initial={{id:42,scenario:"Validar acesso",expectedResult:"Acesso permitido",obtainedResult:"Acesso liberado",status:"EM_PROGRESSO",testSuiteId:9}} onClose={()=>{}} /></QueryClientProvider></MantineProvider>);
};
(window as any).unmount = () => root.unmount();
(window as any).videoEvidence = videoEvidence;
(window as any).montarPlayers = montarPlayers;
`;
const stub = String.raw`
const f = () => (window as any).fixture;
export const driveApi = {
  settings: async () => ({clientId:"fixture.apps.googleusercontent.com",hasClientSecret:true,folder:"folder_123",redirectUri:"http://127.0.0.1:18081/api/drive/callback",connected:f().ready,authorizing:false,account:"qa@example.test"}),
  upload: async (_id:number,blob:Blob,progress:(n:number)=>void) => {
    const current=f(); current.uploads++; current.sizes.push(blob.size);
    if (blob.size < 100) throw Error("Empty recording");
    progress(100);
    if (current.mode === "upload-failure" && current.uploads === 1) throw Error("Falha de envio simulada");
    return {url:"https://drive.google.com/file/d/video_123/view"};
  },
  evidence: async (_id:number,url:string) => {
    const current=f(); current.saves++;
    if (current.mode === "save-failure" && current.saves === 1) throw Error("Falha ao salvar simulada");
    current.persisted=url;
  },
};
`;
async function captureContract() {
  const checks=[];
  let handler, selection=1, calls=0;
  const frame={};
  const window={webContents:{mainFrame:frame,session:{setDisplayMediaRequestHandler:fn=>handler=fn}},isDestroyed:()=>false};
  const context={URL,module:{exports:{}}, require:()=>({
    chooseScreenCapture:async()=>{calls++;return selection ? {id:"screen:1",name:"Tela de teste"} : null;},
  })};
  vm.runInNewContext(fs.readFileSync(path.join(base,"desktop/capture.js"),"utf8"),context);
  context.module.exports.registerScreenCapture(window,()=>"http://127.0.0.1:18081");
  const request={userGesture:true,videoRequested:true,audioRequested:true,frame,securityOrigin:"http://127.0.0.1:18081/"};
  async function run(overrides={}){return new Promise((resolve,reject)=>{Promise.resolve(handler({...request,...overrides},resolve)).catch(reject);});}
  for(const override of [{userGesture:false},{securityOrigin:"https://evil.test"},{frame:{}}]){
    const r=await run(override); if(Object.keys(r).length)throw Error("Untrusted capture allowed");
  }
  if(calls)throw Error("Untrusted request opened chooser");
  const accepted=await run();if(accepted.video.id!=="screen:1"||accepted.audio!=="loopback")throw Error("Capture choice not respected");
  selection=0;const canceled=await run();if(Object.keys(canceled).length)throw Error("Canceled capture started");
  checks.push("trusted-origin-and-frame","explicit-source-selection","cancellation");
  return checks;
}
app.whenReady().then(async()=>{
  const win=new BrowserWindow({show:false,width:1100,height:850,webPreferences:{sandbox:true,backgroundThrottling:false,offscreen:true}});
  win.webContents.on("paint",()=>{}); win.webContents.setFrameRate(20); win.webContents.startPainting();
  const server=http.createServer();
  try {
    win.webContents.on("console-message", (_e, _level, message) => fs.appendFileSync(path.join(out,"console.log"),message+"\n"));
    const contract=await captureContract();
    await new Promise(resolve=>server.listen(0,"127.0.0.1",resolve));
    const origin="http://127.0.0.1:"+server.address().port;
    const results=[];
    for (const project of [base]) {
      const frontend=path.join(project,"frontend");
      const esbuild=require(path.join(frontend,"node_modules/esbuild"));
      const built=await esbuild.build({
        stdin:{contents:fixture,resolveDir:frontend,loader:"tsx"},bundle:true,format:"iife",write:false,
        outfile:path.join(out,"fixture.js"),define:{"process.env.NODE_ENV":'"development"',"import.meta.env.VITE_API_BASE_URL":'""'},
        plugins:[{name:"drive-fixture",setup(build){build.onResolve({filter:/services\/api\/caseMediaApi$/},()=>({path:"case-media",namespace:"media"}));build.onLoad({filter:/.*/,namespace:"media"},()=>({contents:`export const caseMediaApi={list:async()=>[],upload:async(id,field,blob)=>{window.fixture.dbUploads=(window.fixture.dbUploads||0)+1;window.fixture.dbBytes=blob.size;return {id:"stored"};}}`,loader:"ts"}));build.onResolve({filter:/services\/api\/driveApi$/},()=>({path:"drive-api",namespace:"fixture"}));build.onLoad({filter:/.*/,namespace:"fixture"},()=>({contents:stub,loader:"ts"}));}}],
      });
      const js=built.outputFiles.find(f=>f.path.endsWith(".js")).text;
      const css=built.outputFiles.find(f=>f.path.endsWith(".css"))?.text||"";
      server.removeAllListeners("request");
      server.on("request",(req,res)=>{res.setHeader("Content-Type",req.url==="/fixture.js"?"text/javascript":req.url==="/fixture.css"?"text/css":"text/html");res.end(req.url==="/fixture.js"?js:req.url==="/fixture.css"?css:'<!doctype html><meta charset="utf-8"><link rel="stylesheet" href="/fixture.css"><body><script src="/fixture.js"></script>');});
      await win.loadURL(origin);
      const run=code=>win.webContents.executeJavaScript(code);
      const result=await run(`(async()=>{
        const sleep=ms=>new Promise(r=>setTimeout(r,ms));
        const until=async fn=>{for(let i=0;i<400;i++){if(fn())return;await sleep(50);}throw Error("Timed out: "+document.body.innerText);};
        const button=text=>[...document.querySelectorAll("button")].find(b=>b.textContent.includes(text));
        const click=async text=>{await until(()=>button(text)&&!button(text).disabled);button(text).click();await sleep(100);};
        const check=(ok,msg)=>{if(!ok)throw Error(msg);};
        const results=[];
        mountForm();await until(()=>document.querySelectorAll("textarea[id]").length===3);
        const image=new File([await (await fetch(document.querySelector("canvas").toDataURL("image/png"))).blob()],"lightshot.png",{type:"image/png"});
        for(const index of [1,2]) {
          const data=new DataTransfer();data.items.add(image);
          document.querySelectorAll("textarea[id]")[index].dispatchEvent(new ClipboardEvent("paste",{clipboardData:data,bubbles:true,cancelable:true}));
          await sleep(200);
        }
        check(document.querySelectorAll('img[alt="Print a salvar"]').length===2,"Pasted prints not previewed in both results");
        check(document.querySelector('input[accept="video/webm,video/mp4"]'),"Scenario video upload missing");
        results.push({mode:"paste-expected-and-obtained",passed:true});
        return results;
      })()`);
      fs.writeFileSync(path.join(out,"case-form-"+path.basename(project)+".png"),(await win.webContents.capturePage()).toPNG());
      results.push({project,tests:result});
    }
    fs.writeFileSync(path.join(out,"form-results.json"),JSON.stringify({capture:contract,frontends:results},null,2));
    server.close();app.exit(0);
  } catch(error){fs.writeFileSync(path.join(out,"form-error.txt"),error.stack);server.close();app.exit(1);}
});





"use strict";
const { app, BrowserWindow, desktopCapturer, screen, nativeImage } = require("electron");
const fs=require("node:fs"),path=require("node:path"),assert=require("node:assert/strict");
const out=path.resolve(__dirname,"../../drive-validation/capture-picker");
fs.mkdirSync(out,{recursive:true}); app.setPath("userData",path.join(out,"profile"));
const result={tests:[]};
const save=()=>fs.writeFileSync(path.join(out,"results.json"),JSON.stringify(result,null,2));
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const until=async fn=>{for(let i=0;i<150;i++){if(await fn())return;await sleep(40);}throw Error("Timed out");};
setTimeout(()=>{result.error="Timed out";save();app.exit(1);},30000);
app.whenReady().then(async()=>{
  try {
    // Keep all test windows hidden; screenshots contain only synthetic previews.
    BrowserWindow.prototype.show=function(){};
    const parent=new BrowserWindow({show:false,webPreferences:{offscreen:true,backgroundThrottling:false}});
    await parent.loadFile(path.resolve(__dirname,"../loading.html"));
    const png=await parent.webContents.executeJavaScript(`(()=>{const c=document.createElement('canvas');c.width=480;c.height=270;const x=c.getContext('2d');x.fillStyle='#182238';x.fillRect(0,0,480,270);x.fillStyle='#2d3a57';x.fillRect(20,20,440,28);x.fillStyle='#8394b6';x.font='15px sans-serif';x.fillText('Aplicativo de exemplo',35,40);x.fillStyle='#24324a';x.fillRect(20,65,105,185);x.fillStyle='#425574';for(let i=0;i<5;i++)x.fillRect(145,70+i*34,305,20);return c.toDataURL()})()`);
    const thumbnail=nativeImage.createFromDataURL(png);
    const screens=[{id:11,size:{width:1920,height:1080},scaleFactor:1},{id:22,size:{width:2560,height:1440},scaleFactor:1}];
    screen.getAllDisplays=()=>screens;screen.getPrimaryDisplay=()=>screens[0];
    const sources=[
      {id:"screen:11:0",display_id:"11",name:"Screen 1",thumbnail},
      {id:"screen:22:0",display_id:"22",name:"Screen 2",thumbnail},
      {id:"window:31:0",name:"Sistema em teste",thumbnail},
      {id:"window:32:0",name:"Documento de exemplo",thumbnail},
      {id:"window:33:0",name:"<img src=x onerror=alert(1)>",thumbnail},
    ];
    let mode="normal";
    desktopCapturer.getSources=async()=>{if(mode==="error")throw Error("fixture enumeration failure");return mode==="empty"?[]:sources;};
    const {chooseScreenCapture}=require(process.argv[2] ? path.join(process.argv[2],"capture-picker.js") : "../capture-picker");
    async function open(options={}){
      const pending=chooseScreenCapture(parent,options);
      const win=BrowserWindow.getAllWindows().find(w=>w!==parent);
      await until(()=>!win.webContents.isLoading());
      await until(()=>win.webContents.executeJavaScript('Boolean(window.capturePicker && document.querySelector("#refresh") && !document.querySelector("#refresh").disabled)'));
      return {pending,win,run:code=>win.webContents.executeJavaScript(code)};
    }
    let picker=await open();
    assert.equal(await picker.run('document.querySelectorAll(".source").length'),2);
    assert.equal(await picker.run('document.querySelector("#share").disabled'),true);
    assert.equal(await picker.run('document.querySelector(".name").textContent'),"Monitor 1 · principal");
    await picker.run('document.querySelectorAll(".source")[1].click()');
    assert.equal(await picker.run('document.querySelector("#selection").textContent'),"Monitor 2");
    fs.writeFileSync(path.join(out,"monitors.png"),(await picker.win.webContents.capturePage()).toPNG());
    await picker.run('document.querySelector("#share").click()');
    assert.equal((await picker.pending).id,"screen:22:0");result.tests.push("select-second-monitor");
    picker=await open({audioRequested:true});
    await picker.run('document.querySelector(".source").click();document.querySelector("#tab-window").click()');
    assert.equal(await picker.run('document.querySelector("#share").disabled'),true);
    assert.equal(await picker.run('document.querySelectorAll(".source").length'),3);
    assert.ok((await picker.run('document.querySelector("#audio").textContent')).includes("ativado"));
    assert.equal(await picker.run('document.querySelectorAll(".name img").length'),0);
    await picker.run('document.querySelectorAll(".source")[0].click()');
    fs.writeFileSync(path.join(out,"windows.png"),(await picker.win.webContents.capturePage()).toPNG());
    await picker.run('document.querySelector("#share").click()');assert.equal((await picker.pending).id,"window:31:0");
    result.tests.push("window-selection","tab-switch-clears-selection","window-title-is-text","audio-status");
    picker=await open();
    // IDs not offered by this picker cannot finish the request.
    await picker.run('window.capturePicker.select("window:not-offered")');await sleep(60);assert.equal(picker.win.isDestroyed(),false);
    await picker.run('document.querySelector("#cancel").click()');assert.equal(await picker.pending,null);
    result.tests.push("invalid-source-blocked","cancel");
    mode="error";picker=await open();assert.ok((await picker.run('document.querySelector("#message").textContent')).includes("Não foi possível"));
    mode="normal";await picker.run('document.querySelector("#refresh").click()');
    await until(()=>picker.run('document.querySelectorAll(".source").length===2'));
    picker.win.close();assert.equal(await picker.pending,null);result.tests.push("enumeration-error-retry","close-cancels");
    mode="empty";picker=await open();assert.equal(await picker.run('document.querySelector("#share").disabled'),true);
    assert.ok((await picker.run('document.querySelector("#message").textContent')).includes("Nenhum monitor"));
    await picker.run('document.dispatchEvent(new KeyboardEvent("keydown",{key:"Escape"}))');assert.equal(await picker.pending,null);
    result.tests.push("empty-sources","escape-cancels");result.passed=true;save();app.exit(0);
  }catch(error){result.error=error.stack;save();app.exit(1);}
});



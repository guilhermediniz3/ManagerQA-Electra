const { app, BrowserWindow } = require('electron');
const fs=require('fs'),path=require('path'),http=require('http');
const base=path.resolve(__dirname,'../..'),out=path.join(base,'media-library-validation');fs.mkdirSync(out,{recursive:true});
app.setPath('userData',path.join(out,'profile'));
const fixture=`
import React from 'react';import {createRoot} from 'react-dom/client';import {MantineProvider} from '@mantine/core';import {QueryClient,QueryClientProvider} from '@tanstack/react-query';import {MediaLibrary} from './src/features/integrations/MediaLibrary';import '@mantine/core/styles.css';
window.items=Array.from({length:13},(_,i)=>({id:String(i),caseId:i+1,field:i===0?'video':'expected',mime:i===0?'video/webm':'image/png',name:'CT-'+i,scenario:'Validar cenário '+(i+1),suiteId:9,plan:'Plano sintético',size:1024*1024}));window.deleted=[];window.clears=0;
createRoot(document.getElementById('root')).render(<MantineProvider defaultColorScheme='dark'><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><MediaLibrary/></QueryClientProvider></MantineProvider>);
`;
const stub=`
const png='iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jS1cAAAAASUVORK5CYII=';
export const caseMediaApi={library:async(page)=>({items:window.items.slice(page*12,page*12+12),total:window.items.length,bytes:window.items.length*1024*1024,size:12,page}),blob:async(m)=>{window.blobLoads=(window.blobLoads||[]).concat(m.id);return new Blob([Uint8Array.from(atob(png),x=>x.charCodeAt(0))],{type:m.mime});},remove:async(m)=>{window.deleted.push(m.id);window.items=window.items.filter(x=>x.id!==m.id);},clear:async()=>{window.clears++;const deleted=window.items.length;window.items=[];return {deleted};}};
`;
app.whenReady().then(async()=>{
 const win=new BrowserWindow({show:false,width:1250,height:950,webPreferences:{sandbox:true,backgroundThrottling:false}}),server=http.createServer();
 fs.rmSync(path.join(out,'error.txt'),{force:true});
 try{
  const esbuild=require(path.join(base,'frontend/node_modules/esbuild'));
  const built=await esbuild.build({stdin:{contents:fixture,loader:'tsx',resolveDir:path.join(base,'frontend')},bundle:true,format:'iife',write:false,outfile:path.join(out,'fixture.js'),define:{'process.env.NODE_ENV':'"development"'},plugins:[{name:'media',setup(b){b.onResolve({filter:/services\/api\/caseMediaApi$/},()=>({path:'media',namespace:'stub'}));b.onLoad({filter:/.*/,namespace:'stub'},()=>({contents:stub,loader:'ts'}));}}]});
  const js=built.outputFiles.find(f=>f.path.endsWith('.js')).text,css=built.outputFiles.find(f=>f.path.endsWith('.css'))?.text||'';
  server.on('request',(req,res)=>{res.setHeader('Content-Type',req.url==='/app.js'?'text/javascript':req.url==='/app.css'?'text/css':'text/html');res.end(req.url==='/app.js'?js:req.url==='/app.css'?css:'<html><meta charset="utf-8"><link rel="stylesheet" href="/app.css"><div id="root"></div><script src="/app.js"></script></html>');});
  await new Promise(r=>server.listen(0,'127.0.0.1',r));await win.loadURL('http://127.0.0.1:'+server.address().port);
  await win.webContents.executeJavaScript(`new Promise((resolve,reject)=>{let tries=0;const timer=setInterval(()=>{if(document.body.innerText.includes('13 arquivo(s)')){clearInterval(timer);resolve();}else if(++tries>100){clearInterval(timer);reject(Error('List did not load'));}},50);})`);
  fs.writeFileSync(path.join(out,'library.png'),(await win.webContents.capturePage()).toPNG());
  const result=await win.webContents.executeJavaScript(`(async()=>{
   const sleep=ms=>new Promise(r=>setTimeout(r,ms)),until=async fn=>{for(let i=0;i<150;i++){if(fn())return;await sleep(50);}throw Error('Timed out: '+document.body.innerText);};
   const button=text=>[...document.querySelectorAll('button')].find(b=>b.textContent.includes(text));
   const click=async text=>{await until(()=>button(text)&&!button(text).disabled);button(text).click();await sleep(150);};
   const check=(x,msg)=>{if(!x)throw Error(msg);};
   await until(()=>document.body.innerText.includes('13 arquivo(s)'));check(document.body.innerText.includes('13.00 MB'),'Storage count missing');
   check(!window.blobLoads.includes('0'),'Video downloaded before requested');
   await click('Pré-visualizar vídeo');await until(()=>document.querySelector('video'));check(document.querySelector('video').controls,'Video player lacks controls');
   const close=document.querySelector('[role="dialog"] .mantine-Modal-close');check(close,'Video dialog close button missing');close.click();await sleep(300);
   await click('Apagar arquivo');await until(()=>document.body.innerText.includes('Apagar esta evidência?'));await click('Cancelar');check(window.deleted.length===0,'Cancel deleted media');
   await click('Apagar arquivo');await click('Apagar arquivo');
   // The second click may select the card under the modal: explicitly confirm in dialog.
   const dialog=document.querySelector('[role="dialog"]');if(dialog){[...dialog.querySelectorAll('button')].find(b=>b.textContent==='Apagar arquivo').click();}
   await until(()=>window.deleted.length===1);await until(()=>document.body.innerText.includes('12 arquivo(s)'));
   await click('Apagar todas as mídias');await until(()=>document.querySelector('input'));
   check(button('Apagar tudo').disabled,'Bulk delete enabled without confirmation');await click('Cancelar');check(window.clears===0,'Cancel cleared media');
   await click('Apagar todas as mídias');const input=document.querySelector('input');Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,'LIMPAR');input.dispatchEvent(new Event('input',{bubbles:true}));await sleep(150);await click('Apagar tudo');
   await until(()=>window.clears===1);await until(()=>document.body.innerText.includes('0 arquivo(s)'));check(document.body.innerText.includes('0.00 MB'),'Size not updated after clear');
   return {list:true,lazyVideo:true,player:true,cancelIndividual:true,deleteIndividual:true,bulkConfirmation:true,cancelBulk:true,clear:true};
  })()`);
  fs.writeFileSync(path.join(out,'results.json'),JSON.stringify(result,null,2));fs.writeFileSync(path.join(out,'empty.png'),(await win.webContents.capturePage()).toPNG());server.close();app.exit(0);
 }catch(e){fs.writeFileSync(path.join(out,'error.txt'),e.stack);fs.writeFileSync(path.join(out,'error.png'),(await win.webContents.capturePage()).toPNG());server.close();app.exit(1);}
});

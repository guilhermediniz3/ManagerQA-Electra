const {app,BrowserWindow}=require('electron');const fs=require('fs'),path=require('path'),http=require('http');
const base=path.resolve(__dirname,'../..'),out=path.join(base,'password-reset-validation');fs.mkdirSync(out,{recursive:true});app.setPath('userData',path.join(out,'profile'));
const fixture=`import React from 'react';import {createRoot} from 'react-dom/client';import {MantineProvider} from '@mantine/core';import {LoginPage} from './src/features/auth/LoginPage';import '@mantine/core/styles.css';window.calls=[];createRoot(document.getElementById('root')).render(<MantineProvider defaultColorScheme='dark'><LoginPage/></MantineProvider>);`;
const stub=`export const authApi={resetLocalPassword:async(email,password,confirmation)=>{window.calls.push({email,password,confirmation});return {message:'Senha alterada. Entre com a nova senha.'};},login:async()=>({name:'QA',token:'fixture'}),register:async()=>({name:'QA',token:'fixture'})};`;
app.whenReady().then(async()=>{
 const win=new BrowserWindow({show:false,width:1050,height:900,webPreferences:{sandbox:true,backgroundThrottling:false}}),server=http.createServer();
 try{
  const esbuild=require(path.join(base,'frontend/node_modules/esbuild'));const built=await esbuild.build({stdin:{contents:fixture,loader:'tsx',resolveDir:path.join(base,'frontend')},bundle:true,format:'iife',write:false,outfile:path.join(out,'fixture.js'),define:{'process.env.NODE_ENV':'"development"'},plugins:[{name:'auth',setup(b){b.onResolve({filter:/services\/api\/authApi$/},()=>({path:'auth',namespace:'fixture'}));b.onLoad({filter:/.*/,namespace:'fixture'},()=>({contents:stub,loader:'ts'}));}}]});
  const js=built.outputFiles.find(f=>f.path.endsWith('.js')).text,css=built.outputFiles.find(f=>f.path.endsWith('.css'))?.text||'';
  server.on('request',(req,res)=>{res.setHeader('Content-Type',req.url==='/app.js'?'text/javascript':req.url==='/app.css'?'text/css':'text/html');res.end(req.url==='/app.js'?js:req.url==='/app.css'?css:'<html><meta charset="utf-8"><link rel="stylesheet" href="/app.css"><div id="root"></div><script src="/app.js"></script></html>');});
  await new Promise(r=>server.listen(0,'127.0.0.1',r));await win.loadURL('http://127.0.0.1:'+server.address().port);
  await win.webContents.executeJavaScript(`(async()=>{
   const sleep=ms=>new Promise(r=>setTimeout(r,ms)),until=async fn=>{for(let i=0;i<150;i++){if(fn())return;await sleep(40);}throw Error('Timeout: '+document.body.innerText);};
   const click=async text=>{await until(()=>[...document.querySelectorAll('button')].find(b=>b.textContent===text));[...document.querySelectorAll('button')].find(b=>b.textContent===text).click();await sleep(100);};
   const set=(input,value)=>{Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,value);input.dispatchEvent(new Event('input',{bubbles:true}));};
   await click('Criar conta local');await until(()=>document.body.innerText.includes('Criar acesso'));await click('Esqueci minha senha');await until(()=>document.body.innerText.includes('Redefinir senha local'));
   set(document.querySelector('input[type="email"]'),'qa@example.test');set(document.querySelectorAll('input[type="password"]')[0],'abcdef');set(document.querySelectorAll('input[type="password"]')[1],'ghijkl');await sleep(100);await click('Salvar nova senha');await until(()=>document.body.innerText.includes('As senhas não coincidem.'));if(window.calls.length)throw Error('Mismatch submitted');
   set(document.querySelectorAll('input[type="password"]')[1],'abcdef');await sleep(100);
  })()`);
  fs.writeFileSync(path.join(out,'reset.png'),(await win.webContents.capturePage()).toPNG());
  const results=await win.webContents.executeJavaScript(`(async()=>{
   const sleep=ms=>new Promise(r=>setTimeout(r,ms));[...document.querySelectorAll('button')].find(b=>b.textContent==='Salvar nova senha').click();for(let i=0;i<150;i++){if(document.body.innerText.includes('Senha alterada. Entre com a nova senha.'))break;await sleep(40);}
   if(window.calls.length!==1 || window.calls[0].email!=='qa@example.test')throw Error('Reset did not call API exactly once');
   if(!document.body.innerText.includes('Bem-vindo de volta'))throw Error('Did not return to login');if(document.querySelector('input[type="password"]').value!=='')throw Error('Password remained in field');
   return {forgotFromRegistration:true,passwordConfirmation:true,singleRequest:true,returnsToLogin:true,passwordFieldCleared:true};
  })()`);
  fs.writeFileSync(path.join(out,'results.json'),JSON.stringify(results,null,2));server.close();app.exit(0);
 }catch(e){fs.writeFileSync(path.join(out,'error.txt'),e.stack);server.close();app.exit(1);}
});

"use strict";
const fs = require("node:fs"), path = require("node:path"), crypto = require("node:crypto"), vm = require("node:vm");
const asar = require("../node_modules/@electron/asar");
const root = path.resolve(__dirname, "../..");
const installed = path.join(process.env.LOCALAPPDATA, "Programs/QA Manager/resources/app.asar");
const stage = path.join(root, "installer/capture-picker-1.7.4");
const backup = path.join(root, ".backups/capture-picker-" + new Date().toISOString().replace(/[:.]/g, "-"));
const hash = data => crypto.createHash("sha256").update(data).digest("hex");
(async () => {
  const original = fs.readFileSync(installed);
  const version = JSON.parse(asar.extractFile(installed,"package.json").toString()).version;
  if (version !== "1.7.4") throw Error("Expected installed version 1.7.4");
  const files = ["capture.js","capture-picker.js","capture-picker-preload.js","capture-picker-renderer.js","capture-picker.html"];
  const mainSource = fs.readFileSync(path.join(root,"desktop/main.js"));
  if (!mainSource.equals(asar.extractFile(installed,"main.js"))) throw Error("Install the origin/log fix before the picker update");
  fs.mkdirSync(backup, {recursive:true});
  fs.writeFileSync(path.join(backup,"app.asar"), original, {flag:"wx"});
  const packageDir = path.join(stage,"app"); fs.mkdirSync(packageDir,{recursive:true});
  asar.extractAll(installed,packageDir);
  for(const name of files){
    const content=fs.readFileSync(path.join(root,"desktop",name));
    if(name.endsWith(".js"))new vm.Script(content.toString(),{filename:name});
    fs.writeFileSync(path.join(packageDir,name),content);
  }
  const patched=path.join(stage,"app.asar");await asar.createPackage(packageDir,patched);
  const allowed=new Set(files);
  for(const name of asar.listPackage(installed)){
    const relative=name.replace(/^[/\\]/,"");
    if(asar.statFile(installed,relative).files||allowed.has(relative))continue;
    if(!asar.extractFile(installed,relative).equals(asar.extractFile(patched,relative)))throw Error("Unexpected change: "+relative);
  }
  for(const name of files)if(!asar.extractFile(patched,name).equals(fs.readFileSync(path.join(root,"desktop",name))))throw Error("Mismatched package file: "+name);
  const report={installed,patched,backup:path.join(backup,"app.asar"),originalSha256:hash(original),patchedSha256:hash(fs.readFileSync(patched)),files,prepared:true};
  fs.writeFileSync(path.join(stage,"patch.json"),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));
})().catch(error=>{console.error(error);process.exitCode=1;});

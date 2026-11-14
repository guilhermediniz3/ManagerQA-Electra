import { useEffect, useRef, useState } from "react";
import { EditorContent, NodeViewWrapper, ReactNodeViewRenderer, useEditor, type NodeViewProps } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import Image from "@tiptap/extension-image";
import { Alert, Button, Group, Modal, Stack, Text } from "@mantine/core";
import { caseMediaApi, type EvidenceBlock } from "../../services/api/caseMediaApi";
const EvidenceImage=Image.extend({
  addAttributes(){return {...this.parent?.(),mediaId:{default:null},draftId:{default:null},caseId:{default:null}};},
  addNodeView(){return ReactNodeViewRenderer(ImageView);},
});
function ImageView({node,deleteNode}:NodeViewProps) {
  const [url,setUrl]=useState(node.attrs.src||""),[open,setOpen]=useState(false),[error,setError]=useState("");
  useEffect(()=>{let alive=true,local="";
    if(node.attrs.src){setUrl(node.attrs.src);return;}
    if(node.attrs.caseId && node.attrs.mediaId)caseMediaApi.imageBlob(node.attrs.caseId,node.attrs.mediaId).then(blob=>{local=URL.createObjectURL(blob);if(alive)setUrl(local);else URL.revokeObjectURL(local);}).catch(()=>{if(alive)setError("Print removido ou indisponível.");});
    return()=>{alive=false;if(local)URL.revokeObjectURL(local);};
  },[node.attrs.src,node.attrs.mediaId,node.attrs.caseId]);
  return <NodeViewWrapper contentEditable={false} data-evidence-image style={{margin:"8px 0",maxWidth:"100%"}}>
    {url?<img src={url} alt="Print no resultado" onDoubleClick={()=>setOpen(true)} style={{maxWidth:"100%",width:280,maxHeight:260,objectFit:"contain",cursor:"zoom-in"}} />:<Text size="xs">{error||"Carregando print…"}</Text>}
    <Group gap="xs"><Button type="button" size="compact-xs" variant="subtle" onClick={()=>setOpen(true)}>Ampliar</Button><Button type="button" size="compact-xs" color="red" variant="subtle" onClick={deleteNode}>Retirar do texto</Button></Group>
    <Modal opened={open} onClose={()=>setOpen(false)} fullScreen title="Print ampliado"><img src={url} alt="Print ampliado" style={{maxWidth:"100%",maxHeight:"85vh",objectFit:"contain"}} /></Modal>
  </NodeViewWrapper>;
}
export function EvidenceEditor({label,blocks,caseId,onChange,onFile,disabled=false}:{label:string;blocks:EvidenceBlock[];caseId?:number;onChange:(blocks:EvidenceBlock[])=>void;onFile:(file:File)=>Extract<EvidenceBlock,{type:"image"}>;disabled?:boolean}) {
  const change=useRef(onChange);change.current=onChange;const add=useRef(onFile);add.current=onFile;
  const input=useRef<HTMLInputElement>(null),[error,setError]=useState("");
  const insert=(files:File[])=>{if(!editor)return;for(const file of files){if(!["image/png","image/jpeg","image/webp"].includes(file.type)||file.size>10*1024*1024){setError("Use prints PNG/JPG/WebP de até 10 MB.");continue;}setError("");const block=add.current(file);editor.chain().focus().insertContent([{type:"image",attrs:{...block,caseId}},{type:"paragraph"}]).run();}};
  const editor=useEditor({
    extensions:[StarterKit.configure({heading:false,bulletList:false,orderedList:false,listItem:false,blockquote:false,codeBlock:false,horizontalRule:false,bold:false,italic:false,strike:false,code:false,link:false,underline:false}),EvidenceImage],
    content:{type:"doc",content:blocks.length?blocks.map(b=>b.type==="text"?{type:"paragraph",content:b.text?[{type:"text",text:b.text}]:[]}:{type:"image",attrs:{...b,caseId}}):[{type:"paragraph"}]},
    editable:!disabled,
    onUpdate:({editor})=>{const nodes=editor.getJSON().content||[];change.current(nodes.map(n=>n.type==="image"?{type:"image",mediaId:n.attrs?.mediaId||undefined,draftId:n.attrs?.draftId||undefined,src:n.attrs?.src||undefined}:{type:"text",text:(n.content||[]).map(c=>c.type==="hardBreak"?"\n":("text" in c ? c.text : "")||"").join("")}));},
    editorProps:{attributes:{role:"textbox","aria-label":label,"aria-multiline":"true",style:"min-height:100px;outline:none;padding:10px;white-space:pre-wrap;overflow-wrap:anywhere;"},handlePaste:(_view,event)=>{
      const files=Array.from(event.clipboardData?.files||[]);if(files.length){event.preventDefault();insert(files);return true;}
      const text=event.clipboardData?.getData("text/plain");if(text){event.preventDefault();editor?.commands.insertContent(text.split(/\r?\n/).map(line=>({type:"paragraph",content:line?[{type:"text",text:line}]:[]})));return true;}return false;
    }},
  });
  useEffect(()=>{editor?.setEditable(!disabled);},[disabled,editor]);
  return <Stack gap={4}><Text component="label" fw={500} size="sm">{label}</Text><Text size="xs" c="dimmed">Escreva, cole um print com Ctrl+V e continue escrevendo abaixo. A ordem será mantida no Jira.</Text>
    <div style={{border:"1px solid var(--mantine-color-default-border)",borderRadius:6,background:"var(--mantine-color-body)"}}><EditorContent editor={editor} /></div>
    <input ref={input} type="file" accept="image/png,image/jpeg,image/webp" multiple hidden onChange={e=>{insert(Array.from(e.currentTarget.files||[]));e.currentTarget.value="";}} />
    <Group><Button type="button" variant="subtle" size="xs" disabled={disabled} onClick={()=>input.current?.click()}>Inserir print no cursor</Button></Group>{error&&<Alert color="red">{error}</Alert>}
  </Stack>;
}

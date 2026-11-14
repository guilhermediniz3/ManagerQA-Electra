import { useEffect, useState } from "react";
import { Alert, Button, Group, Modal, Stack, Text } from "@mantine/core";
import { caseMediaApi, type CaseMedia } from "../../services/api/caseMediaApi";
export function CaseMediaPanel({caseId,field,revision=0}: {caseId:number;field:string;revision?:number}) {
  const [items,setItems]=useState<CaseMedia[]>([]), [error,setError]=useState("");
  const reload=()=>caseMediaApi.list(caseId).then(x=>setItems(x.filter(m=>m.field===field))).catch(e=>setError(e.message));
  useEffect(()=>{void reload();},[caseId,field,revision]);
  return <Stack gap="xs">{error && <Alert color="red">{error}</Alert>}{items.map(m=><Media key={m.id} media={m} remove={async()=>{await caseMediaApi.remove(m);void reload();}} />)}</Stack>;
}
function Media({media,remove}:{media:CaseMedia;remove:()=>Promise<void>}) {
  const [url,setUrl]=useState(""),[open,setOpen]=useState(false),[error,setError]=useState("");
  useEffect(()=>{let alive=true,local="";caseMediaApi.blob(media).then(b=>{local=URL.createObjectURL(b);if(alive)setUrl(local);else URL.revokeObjectURL(local);}).catch(e=>{if(alive)setError(e.message);});return()=>{alive=false;if(local)URL.revokeObjectURL(local);};},[media.id]);
  const video=media.mime.startsWith("video/");
  return <Stack gap={4}><Text size="sm">{video?"Vídeo armazenado no banco":"Print anexado"} · {(media.size/1024/1024).toFixed(1)} MB</Text>{error && <Alert color="red">{error}</Alert>}{url && (video?<video src={url} controls playsInline preload="metadata" style={{width:"100%",maxHeight:240,background:"#111"}} />:<img src={url} alt="Print do resultado" onClick={()=>setOpen(true)} style={{maxWidth:240,maxHeight:160,objectFit:"contain",cursor:"zoom-in"}} />)}<Group><Button type="button" size="xs" variant="subtle" onClick={()=>setOpen(true)}>Ampliar</Button><Button type="button" size="xs" color="red" variant="subtle" onClick={()=>void remove().catch(e=>setError(e.message))}>Remover</Button></Group><Modal opened={open} onClose={()=>setOpen(false)} fullScreen title="Evidência">{video?<video src={url} controls autoPlay style={{width:"100%",height:"85vh"}} />:<img src={url} alt="Print ampliado" style={{maxWidth:"100%"}} />}</Modal></Stack>;
}

import { useEffect, useRef, useState } from "react";
import { Alert, Badge, Button, Card, Group, Modal, Pagination, SimpleGrid, Stack, Text, TextInput, Title } from "@mantine/core";
import { useMutation } from "@tanstack/react-query";
import { useResource, useRefresh } from "../../hooks/useResource";
import { caseMediaApi, type ManagedMedia } from "../../services/api/caseMediaApi";
import { ResourceState } from "../../components/feedback/ResourceState";
const size = (bytes:number) => bytes>=1024**3 ? (bytes/1024**3).toFixed(2)+" GB" : (bytes/1024**2).toFixed(2)+" MB";
export function MediaLibrary() {
  const [page,setPage]=useState(1), [target,setTarget]=useState<ManagedMedia|"all"|null>(null),[confirmation,setConfirmation]=useState("");
  const refresh=useRefresh();
  const query=useResource(["media-library",page],()=>caseMediaApi.library(page-1));
  const pages=Math.max(1,Math.ceil((query.data?.total||0)/12));
  useEffect(()=>{if(query.data && page>pages)setPage(pages);},[query.data,page,pages]);
  const remove=useMutation({mutationFn:async()=>{if(target==="all")return caseMediaApi.clear();if(target)await caseMediaApi.remove(target);},onSuccess:()=>{setTarget(null);setConfirmation("");refresh();}});
  const close=()=>{if(!remove.isPending){setTarget(null);setConfirmation("");remove.reset();}};
  return <Stack mt="xl">
    <Group justify="space-between"><div><Title order={2}>Gerenciamento de mídia</Title><Text c="dimmed" size="sm">Imagens e vídeos armazenados no banco das evidências dos cenários.</Text></div><Button color="red" variant="light" disabled={!query.data?.total} onClick={()=>{remove.reset();setTarget("all");}}>Apagar todas as mídias</Button></Group>
    <Text size="sm">{query.data?.total||0} arquivo(s) · {size(query.data?.bytes||0)} armazenados</Text>
    <Text size="sm" c="dimmed">A limpeza remove os arquivos dos cenários. Links Vimeo/Drive e arquivos já enviados ao Jira ou ao Drive são mantidos.</Text>
    <ResourceState loading={query.isLoading} error={query.error} empty={query.data?.total===0} retry={()=>query.refetch()} />
    <SimpleGrid cols={{base:1,sm:2,xl:3}}>{query.data?.items.map(media=><MediaCard key={media.id} media={media} onRemove={()=>{remove.reset();setTarget(media);}} />)}</SimpleGrid>
    {pages>1 && <Pagination total={pages} value={page} onChange={setPage} />}
    <Modal opened={target!==null} onClose={close} title={target==="all"?"Apagar todas as mídias?":"Apagar esta evidência?"} centered closeOnClickOutside={!remove.isPending} closeOnEscape={!remove.isPending} withCloseButton={!remove.isPending}>
      <Stack><Text>{target==="all"?"Todos os vídeos e imagens armazenados no banco serão removidos dos cenários. Esta ação não pode ser desfeita.":`O arquivo será removido do cenário CT ${target?.caseId}. Esta ação não pode ser desfeita.`}</Text>
        {target==="all" && <TextInput label="Digite LIMPAR para confirmar" value={confirmation} onChange={e=>setConfirmation(e.currentTarget.value)} disabled={remove.isPending} />}
        {remove.error && <Alert color="red">{remove.error.message}</Alert>}
        <Group justify="flex-end"><Button variant="default" onClick={close} disabled={remove.isPending}>Cancelar</Button><Button color="red" loading={remove.isPending} disabled={target==="all" && confirmation!=="LIMPAR"} onClick={()=>remove.mutate()}>{target==="all"?"Apagar tudo":"Apagar arquivo"}</Button></Group>
      </Stack>
    </Modal>
  </Stack>;
}
function MediaCard({media,onRemove}:{media:ManagedMedia;onRemove:()=>void}) {
  const alive=useRef(true);
  useEffect(()=>{alive.current=true;return()=>{alive.current=false;};},[]);
  const [url,setUrl]=useState(""),[open,setOpen]=useState(false),[loading,setLoading]=useState(false),[error,setError]=useState("");
  const video=media.mime.startsWith("video/");
  useEffect(()=>{let active=true,local="";
    if(!video){setLoading(true);caseMediaApi.blob(media).then(blob=>{local=URL.createObjectURL(blob);if(active)setUrl(local);else URL.revokeObjectURL(local);}).catch(e=>{if(active)setError(e.message);}).finally(()=>{if(active)setLoading(false);});}
    return()=>{active=false;if(local)URL.revokeObjectURL(local);};
  },[media.id]);
  useEffect(()=>()=>{if(video && url)URL.revokeObjectURL(url);},[url]);
  const play=async()=>{setError("");if(url){setOpen(true);return;}setLoading(true);try{const blob=await caseMediaApi.blob(media);if(!alive.current)return;setUrl(URL.createObjectURL(blob));setOpen(true);}catch(e){if(alive.current)setError(e instanceof Error?e.message:"Não foi possível abrir a mídia.");}finally{if(alive.current)setLoading(false);}};
  return <Card withBorder><Stack gap="xs"><Group justify="space-between"><Badge>{video?"Vídeo":"Imagem"}</Badge><Text size="xs">{size(media.size)}</Text></Group>
    {video?<Button variant="light" h={130} loading={loading} onClick={()=>void play()}>▶ Pré-visualizar vídeo</Button>:<button type="button" onClick={()=>setOpen(true)} aria-label="Ampliar imagem" style={{border:0,background:"transparent",cursor:"zoom-in",height:130}}>{url?<img src={url} alt={`Evidência CT ${media.caseId}`} style={{width:"100%",height:130,objectFit:"contain"}} />:<Text>{loading?"Carregando imagem…":"Imagem indisponível"}</Text>}</button>}
    <Text fw={600} size="sm" lineClamp={2}>CT {media.caseId} · {media.scenario}</Text><Text size="xs" c="dimmed">{media.field==="expected"?"Resultado esperado":media.field==="obtained"?"Resultado obtido":"Vídeo do cenário"} · Suíte {media.suiteId??"—"}</Text><Text size="xs" c="dimmed" lineClamp={1}>{media.plan}</Text>
    {error && <Alert color="red">{error}</Alert>}<Button color="red" variant="subtle" onClick={onRemove}>Apagar arquivo</Button></Stack>
    <Modal opened={open} onClose={()=>setOpen(false)} title={`Evidência · CT ${media.caseId}`} size="xl">{video?<video controls autoPlay src={url} style={{width:"100%",maxHeight:"75vh"}} />:<img src={url} alt="Evidência ampliada" style={{width:"100%",maxHeight:"75vh",objectFit:"contain"}} />}</Modal>
  </Card>;
}

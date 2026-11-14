import { EvidenceEditor } from "./EvidenceEditor";
import { caseMediaApi, type CaseContent, type EvidenceBlock } from "../../services/api/caseMediaApi";
import { CaseMediaPanel } from "./CaseMediaPanel";
import { useEffect, useRef, useState } from "react";
import {
  Modal,
  Stack,
  TextInput,
  Textarea,
  Select,
  Button,
  Text,
  Alert,
} from "@mantine/core";
import { useMutation } from "@tanstack/react-query";
import { testCaseApi } from "../../services/api/testCaseApi";
import type { TestCase, CaseStatus } from "../../types/testCase.types";
import { useRefresh } from "../../hooks/useResource";
import { statusOptions } from "../../utils/status";
import { VimeoEvidence } from "./VimeoEvidence";
import { videoEvidence } from "../../utils/videoEvidence";
import { ScenarioRecorder } from "./ScenarioRecorder";
export function TestCaseForm({
  initial,
  onClose,
}: {
  initial: Partial<TestCase>;
  onClose: () => void;
}) {
  const [form, setForm] = useState(initial);
  const [record, setRecord] = useState(false);
  const refresh = useRefresh();
  const [revision, setRevision] = useState(0);
  const [mediaError,setMediaError] = useState("");
  const [ready,setReady] = useState(!initial.id);
  const [documents,setDocuments] = useState<CaseContent>({expected:(initial.expectedResult||"").split("\n").map(text=>({type:"text",text})),obtained:(initial.obtainedResult||"").split("\n").map(text=>({type:"text",text}))});
  const [pending, setPending] = useState<{field: string; file: File; draftId?:string}[]>([]);
  const uploaded=useRef<Record<string,string>>({}), urls=useRef<string[]>([]);
  useEffect(()=>{let active=true;if(initial.id)caseMediaApi.content(initial.id).then(doc=>{if(active){setDocuments(doc);setReady(true);}}).catch(e=>{if(active)setMediaError(e.message);});return()=>{active=false;};},[initial.id]);
  useEffect(()=>()=>urls.current.forEach(url=>URL.revokeObjectURL(url)),[]);
  const addFile=(field:string)=>(file:File):Extract<EvidenceBlock,{type:"image"}>=>{
    const draftId=crypto.randomUUID(),src=URL.createObjectURL(file);urls.current.push(src);
    setPending(p=>[...p,{field,file,draftId}]);return {type:"image",draftId,src};
  };
  const change=(field:"expected"|"obtained")=>(blocks:EvidenceBlock[])=>setDocuments(d=>({...d,[field]:blocks}));
  const save = useMutation({
    mutationFn: async () => {
      if(!documents.expected.some(b=>b.type==="image" || b.text.trim()))throw new Error("Preencha o resultado esperado com texto ou print.");
      const plain=(blocks:EvidenceBlock[])=>blocks.filter((b):b is Extract<EvidenceBlock,{type:"text"}>=>b.type==="text").map(b=>b.text).join("\n");
      const saved = await testCaseApi.save({...form,expectedResult:plain(documents.expected),obtainedResult:plain(documents.obtained)});
      setForm(saved);
      const content:CaseContent={expected:[],obtained:[]};
      for(const field of ["expected","obtained"] as const)for(const block of documents[field]){
        if(block.type==="text"){content[field].push(block);continue;}
        let mediaId=block.mediaId;
        if(!mediaId && block.draftId){
          mediaId=uploaded.current[block.draftId];
          if(!mediaId){const item=pending.find(p=>p.draftId===block.draftId);if(!item)throw new Error("Print pendente não encontrado.");mediaId=(await caseMediaApi.upload(saved.id,field,item.file)).id;uploaded.current[block.draftId]=mediaId;}
        }
        if(mediaId)content[field].push({type:"image",mediaId});
      }
      await caseMediaApi.saveContent(saved.id,content);
      for (const item of pending.filter(p=>p.field==="video")) {
        await caseMediaApi.upload(saved.id, item.field, item.file);
        setPending(p => p.filter(x => x !== item));
      }
      return saved;
    },
    onSuccess: () => {
      refresh();
      onClose();
    },
  });
  return (
    <Modal
      opened
      onClose={onClose}
      title={form.id ? "Editar / executar caso de teste" : "Novo caso de teste"}
      size="lg"
    >
      <form
        onSubmit={(e) => {
          e.preventDefault();
          save.mutate();
        }}
      >
        <Stack>
          {mediaError && <Alert color="red">{mediaError}</Alert>}
          {save.error && <Alert color="red">{save.error.message}</Alert>}
          <Textarea
            label="Cenário e passos"
            maxLength={255}
            required
            autosize
            minRows={3}
            value={form.scenario || ""}
            onChange={(e) => setForm({ ...form, scenario: e.target.value })}
          />
          {!ready && <Text>Carregando conteúdo dos resultados…</Text>}
          {ready && <><EvidenceEditor label="Resultado esperado" blocks={documents.expected} caseId={form.id} onChange={change("expected")} onFile={addFile("expected")} disabled={save.isPending} />
          <EvidenceEditor label="Resultado obtido" blocks={documents.obtained} caseId={form.id} onChange={change("obtained")} onFile={addFile("obtained")} disabled={save.isPending} /></>}
          <Select
            label="Resultado de execução"
            data={statusOptions}
            value={form.status || "EM_PROGRESSO"}
            onChange={(v) => setForm({ ...form, status: v as CaseStatus })}
          />
          <TextInput
            label="Evidência"
            value={form.videoEvidence || ""}
            error={
              form.videoEvidence && !videoEvidence(form.videoEvidence)
                ? "URL de vídeo inválida (Vimeo ou Drive)"
                : undefined
            }
            onChange={(e) =>
              setForm({ ...form, videoEvidence: e.target.value })
            }
          />
          <VimeoEvidence url={form.videoEvidence || ""} />
          {form.id && <CaseMediaPanel caseId={form.id} field="video" revision={revision} />}
          <Text size="sm">Ou anexe um vídeo WebM/MP4 ao cenário (até 512 MB):</Text>
          <input type="file" accept="video/webm,video/mp4" aria-label="Anexar vídeo ao cenário" onChange={e => {const file=e.target.files?.[0];if(file)setPending(p=>[...p.filter(x=>x.field!=="video"),{field:"video",file}]);e.target.value="";}} />
          {pending.filter(p=>p.field==="video").map((p,i)=><DraftPrint key={i} file={p.file} remove={()=>setPending(items=>items.filter(x=>x!==p))} />)}
          <Button type="button" color="red" variant="light" disabled={!form.id} onClick={() => setRecord(true)}>● REC · Gravar cenário{!form.id ? " (salve o caso primeiro)" : ""}</Button>
          <Button
            type="submit"
            loading={save.isPending}
            disabled={!ready || (!!form.videoEvidence && !videoEvidence(form.videoEvidence))}
          >
            Salvar caso
          </Button>
        </Stack>
      </form>
      {record && form.id && <ScenarioRecorder caseId={form.id} scenario={form.scenario || ""} previousUrl={form.videoEvidence} onSaved={url => { setForm(f => ({...f, videoEvidence: url})); setRevision(r => r+1); refresh(); }} onClose={() => setRecord(false)} />}
    </Modal>
  );
}

function DraftPrint({file,remove}:{file:File;remove:()=>void}) {
  const [url]=useState(()=>URL.createObjectURL(file));
  useEffect(()=>()=>URL.revokeObjectURL(url),[url]);
  return <div>{file.type.startsWith("video/") ? <video src={url} controls style={{width:"100%",maxHeight:240}} /> : <img src={url} alt="Print a salvar" style={{maxWidth:240,maxHeight:160}} />}<Button type="button" variant="subtle" onClick={remove}>Remover anexo</Button><Text size="xs">Será armazenado ao salvar o caso.</Text></div>;
}

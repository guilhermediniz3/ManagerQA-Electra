import { httpClient as h } from "./httpClient";
export interface CaseMedia { id: string; caseId: number; field: string; mime: string; name: string; size: number }
export type EvidenceBlock = {type:"text";text:string}|{type:"image";mediaId?:string;draftId?:string;src?:string};
export interface CaseContent {expected:EvidenceBlock[];obtained:EvidenceBlock[]}
export interface ManagedMedia extends CaseMedia {scenario:string; suiteId:number|null; plan:string|null}
const path = (id: number) => `/api/cases/${id}/media`;
export const caseMediaApi = {
  content: async(id:number)=>(await h.get<CaseContent>(`/api/cases/${id}/content`)).data,
  saveContent: async(id:number,content:CaseContent)=>(await h.put<CaseContent>(`/api/cases/${id}/content`,content)).data,
  imageBlob: async(caseId:number,id:string)=>(await h.get<Blob>(`${path(caseId)}/${id}`,{responseType:"blob"})).data,
  library: async (page: number) => (await h.get<{items:ManagedMedia[];total:number;bytes:number;page:number;size:number}>("/api/media-library",{params:{page,size:12}})).data,
  clear: async () => (await h.delete<{deleted:number}>("/api/media-library",{params:{confirmation:"LIMPAR"}})).data,
  list: async (id: number) => (await h.get<CaseMedia[]>(path(id))).data,
  upload: async (id: number, field: string, file: Blob) => {
    const data = new FormData(); data.append("field", field);
    data.append("file", new Blob([file], {type: file.type.split(";")[0]}), "evidencia");
    return (await h.post<CaseMedia>(path(id), data, {headers: {"Content-Type": undefined}, timeout: 35*60*1000})).data;
  },
  blob: async (m: CaseMedia) => (await h.get<Blob>(`${path(m.caseId)}/${m.id}`, {responseType: "blob", timeout: 35*60*1000})).data,
  remove: async (m: CaseMedia) => { await h.delete(`${path(m.caseId)}/${m.id}`); },
};

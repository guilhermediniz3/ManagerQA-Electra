import { httpClient as h } from "./httpClient";
import type { Demand, DemandLocal } from "../../types/demand.types";
import type { AiSummary } from "../../types/ai.types";
import type { CaseStatus } from "../../types/testCase.types";

export interface SuitePreview {
  title: string;
  rows: string[][];
  /** Quantos casos entraram depois do filtro — 0 é recusado pelo backend. */
  cases: number;
}
export const demandApi = {
  list: async () => (await h.get<Demand[]>("/api/demands")).data,
  get: async (key: string) => (await h.get<Demand>("/api/demands/" + key)).data,
  sync: async (key: string) =>
    (await h.post<Demand>("/api/demands/" + key + "/sync")).data,
  syncQuiet: async (key: string) =>
    (
      await h.post<Demand>("/api/demands/" + key + "/sync", undefined, {
        silentError: true,
      })
    ).data,
  save: async (key: string, input: DemandLocal) =>
    (await h.patch<Demand>("/api/demands/" + key + "/local", input)).data,
  // `statuses` vazio envia a suíte inteira; com valores, só os casos naqueles
  // status. O mesmo filtro alimenta a prévia e o envio, para que o que a pessoa
  // vê na tela seja exatamente o que vai para o Jira.
  suiteComment: async (key: string, suiteId: number, statuses: CaseStatus[] = []) =>
    (await h.get<SuitePreview>("/api/demands/" + key + "/suite-comment", {
      params: {suiteId, ...(statuses.length ? {statuses: statuses.join(",")} : {})},
    })).data,
  transition: async (key: string, transitionId: string, suiteId?: number, statuses: CaseStatus[] = []) =>
    (
      await h.post<Demand>("/api/demands/" + key + "/transitions", {
        transitionId,
        ...(suiteId === undefined ? {} : {suiteId}),
        ...(suiteId === undefined || !statuses.length ? {} : {statuses}),
      })
    ).data,
  remove: async (key: string) => {
    await h.delete("/api/demands/" + key);
  },
  saveAiSummary: async (key: string, input: AiSummary) =>
    (await h.put<Demand>("/api/demands/" + key + "/ai-summary", input)).data,
  plan: async (key: string) =>
    (await h.post<Demand>("/api/demands/" + key + "/test-plan")).data,
};

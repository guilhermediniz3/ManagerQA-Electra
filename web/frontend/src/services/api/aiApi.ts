import { httpClient as h, TEMPO_DA_IA } from "./httpClient";
import type { CaseDraft } from "../../types/testCase.types";
import type { AiSummary, EstadoDaIaLocal, ModeloLocal } from "../../types/ai.types";
export const aiApi = {
  /** Estado da IA desta máquina: se está no ar, o que há baixado e o que dá para baixar. */
  local: async () => (await h.get<EstadoDaIaLocal>("/api/ai/local")).data,
  /** Baixar é lento de propósito: são gigabytes, e a resposta só vem no fim. */
  baixarModeloLocal: async (name: string) =>
    (
      await h.post<ModeloLocal>(
        "/api/ai/local/models",
        { name },
        { timeout: 30 * 60 * 1000 },
      )
    ).data,
  removerModeloLocal: async (name: string) => {
    await h.delete("/api/ai/local/models/" + encodeURIComponent(name));
  },
  models: async (provider: string) =>
    (
      await h.get<string[]>("/api/ai/models", {
        params: { provider },
        timeout: 60000,
      })
    ).data,
  summary: async (provider: string, description: string) =>
    (
      await h.post<AiSummary>(
        "/api/ai/summary",
        { provider, description },
        { timeout: TEMPO_DA_IA },
      )
    ).data,
  preview: async (provider: string, description: string) =>
    (
      await h.post<CaseDraft[]>(
        "/api/ai/preview",
        { provider, description },
        { timeout: TEMPO_DA_IA },
      )
    ).data,
  import: async (suiteId: number, cases: CaseDraft[]) => {
    await h.post("/api/ai/import", { suiteId, cases });
  },
};

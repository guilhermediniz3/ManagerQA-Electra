import { httpClient as h } from "./httpClient";
import type { GherkinFeature } from "../../types/gherkin.types";

export const gherkinApi = {
  byPlan: async (planId: number) =>
    (await h.get<GherkinFeature[]>("/gherkin/testplan/" + planId)).data,
  get: async (id: number) =>
    (await h.get<GherkinFeature>("/gherkin/" + id)).data,
  save: async (item: Partial<GherkinFeature>) =>
    item.id
      ? (await h.put<GherkinFeature>("/gherkin/" + item.id, item)).data
      : (await h.post<GherkinFeature>("/gherkin", item)).data,
  remove: async (id: number) => {
    await h.delete("/gherkin/" + id);
  },
  /** Converte a suíte e guarda o resultado na aba Gherkin do plano. */
  fromSuite: async (suiteId: number, nome?: string) =>
    (
      await h.post<GherkinFeature>(
        "/gherkin/from-suite/" + suiteId + (nome ? "?nome=" + encodeURIComponent(nome) : ""),
      )
    ).data,
};

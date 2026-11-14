import { httpClient as h } from "./httpClient";
import type { TestPlan } from "../../types/testPlan.types";
export const testPlanApi = {
  list: async () => (await h.get<TestPlan[]>("/testplans")).data,
  get: async (id: number) => (await h.get<TestPlan>("/testplans/" + id)).data,
  save: async (item: Partial<TestPlan>) =>
    item.id
      ? (await h.put<TestPlan>("/testplans/" + item.id, item)).data
      : (await h.post<TestPlan>("/testplans", item)).data,
  remove: async (id: number) => {
    await h.delete("/testplans/" + id);
  },
};

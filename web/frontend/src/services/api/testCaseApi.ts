import { httpClient as h } from "./httpClient";
import type { TestCase } from "../../types/testCase.types";
export const testCaseApi = {
  list: async () => (await h.get<TestCase[]>("/testcases")).data,
  get: async (id: number) => (await h.get<TestCase>("/testcases/" + id)).data,
  save: async (item: Partial<TestCase>) =>
    item.id
      ? (await h.put<TestCase>("/testcases/" + item.id, item)).data
      : (await h.post<TestCase>("/testcases", item)).data,
  remove: async (id: number) => {
    await h.delete("/testcases/" + id);
  },
  bySuite: async (plan: number, suite: number) =>
    (await h.get<TestCase[]>("/testcases/plan/" + plan + "/suite/" + suite))
      .data,
};

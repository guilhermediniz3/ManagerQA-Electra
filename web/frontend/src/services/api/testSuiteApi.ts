import { httpClient as h } from "./httpClient";
import type { TestSuite } from "../../types/testSuite.types";
export const testSuiteApi = {
  list: async () => (await h.get<TestSuite[]>("/test-suites")).data,
  get: async (id: number) =>
    (await h.get<TestSuite>("/test-suites/" + id)).data,
  save: async (item: Partial<TestSuite>) =>
    item.id
      ? (await h.put<TestSuite>("/test-suites/" + item.id, item)).data
      : (await h.post<TestSuite>("/test-suites", item)).data,
  remove: async (id: number) => {
    await h.delete("/test-suites/" + id);
  },
  byPlan: async (id: number) =>
    (await h.get<TestSuite[]>("/test-suites/testplan/" + id)).data,
  clone: async (id: number, item: Partial<TestSuite>) =>
    (await h.post<TestSuite>("/test-suites/clone/" + id, item)).data,
};

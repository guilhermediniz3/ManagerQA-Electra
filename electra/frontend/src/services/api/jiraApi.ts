import { httpClient as h } from "./httpClient";
import type {
  JiraSearch,
  JiraNamed,
  JiraUser,
  JiraValues,
  JiraTransition,
} from "../../types/jira.types";
const MAX_REFERENCE_PAGES = 200;

async function allReferencePages(url: string): Promise<JiraValues> {
  const items = new Map<string, JiraNamed>();
  let startAt = 0;
  for (let pageNumber = 0; pageNumber < MAX_REFERENCE_PAGES; pageNumber++) {
    const { data: page } = await h.get<
      JiraValues & { startAt?: number; maxResults?: number }
    >(url, { params: { startAt } });
    if (!Array.isArray(page.values)) {
      throw new Error("O Jira retornou uma página de referências inválida.");
    }
    const returnedStart = page.startAt ?? startAt;
    if (!Number.isSafeInteger(returnedStart) || returnedStart !== startAt) {
      throw new Error("A paginação do Jira não avançou como esperado.");
    }
    const previousSize = items.size;
    for (const item of page.values) items.set(String(item.id), item);
    const step = page.maxResults ?? page.values.length;
    if (!Number.isSafeInteger(step) || step < 0) {
      throw new Error("O Jira retornou um tamanho de página inválido.");
    }
    const nextStart = returnedStart + step;
    const complete =
      page.isLast === true ||
      (page.total !== undefined &&
        returnedStart + page.values.length >= page.total) ||
      (page.isLast === undefined && page.values.length === 0) ||
      (page.isLast === undefined &&
        page.maxResults !== undefined &&
        page.values.length < page.maxResults);
    if (complete) {
      const values = [...items.values()];
      return { values, total: values.length, isLast: true };
    }
    if (nextStart <= startAt || items.size === previousSize) {
      throw new Error("O Jira repetiu uma página sem novos resultados.");
    }
    startAt = nextStart;
  }
  throw new Error("O Jira excedeu o limite de páginas de referências.");
}
export const jiraApi = {
  search: async (jql: string, cursor?: string) =>
    (await h.get<JiraSearch>("/api/jira/issues", { params: { jql, cursor } }))
      .data,
  searchQuiet: async (jql: string, cursor?: string) =>
    (
      await h.get<JiraSearch>("/api/jira/issues", {
        params: { jql, cursor },
        silentError: true,
      })
    ).data,
  statuses: async () => (await h.get<JiraNamed[]>("/api/jira/statuses")).data,
  fields: async () => (await h.get<JiraNamed[]>("/api/jira/fields")).data,
  projects: () => allReferencePages("/api/jira/projects"),
  boards: () => allReferencePages("/api/jira/boards"),
  sprints: (id: string) =>
    allReferencePages("/api/jira/boards/" + id + "/sprints"),
  users: async (query: string) =>
    (await h.get<JiraUser[]>("/api/jira/users", { params: { query } })).data,
  importUser: async (accountId: string, kind: string) => {
    await h.post("/api/jira/users/import", { accountId, kind });
  },
  transitions: async (key: string) =>
    (
      await h.get<{ transitions: JiraTransition[] }>(
        "/api/jira/issues/" + key + "/transitions",
      )
    ).data.transitions,
};

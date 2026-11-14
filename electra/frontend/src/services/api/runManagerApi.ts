import { httpClient as h } from "./httpClient";
import type {
  RunCatalog,
  RunContainer,
  RunCard,
  RunEnvironment,
  RunExecution,
  RunFailure,
  RunLog,
  RunManualCase,
  RunMetrics,
  RunProjectRead,
  RunSystem,
  RunTag,
  RunVersion,
} from "../../types/runmanager.types";

const base = "/runmanager";

export const runManagerApi = {
  // cadastro
  catalog: async () => (await h.get<RunCatalog>(base + "/catalog")).data,

  saveSystem: async (s: RunSystem) =>
    s.id
      ? (await h.put<RunSystem>(base + "/catalog/systems/" + s.id, s)).data
      : (await h.post<RunSystem>(base + "/catalog/systems", s)).data,
  removeSystem: async (id: number) => {
    await h.delete(base + "/catalog/systems/" + id);
  },

  saveVersion: async (v: RunVersion) =>
    v.id
      ? (await h.put<RunVersion>(base + "/catalog/versions/" + v.id, v)).data
      : (await h.post<RunVersion>(base + "/catalog/versions", v)).data,
  removeVersion: async (id: number) => {
    await h.delete(base + "/catalog/versions/" + id);
  },

  saveEnvironment: async (e: RunEnvironment) =>
    e.id
      ? (await h.put<RunEnvironment>(base + "/catalog/environments/" + e.id, e)).data
      : (await h.post<RunEnvironment>(base + "/catalog/environments", e)).data,
  removeEnvironment: async (id: number) => {
    await h.delete(base + "/catalog/environments/" + id);
  },

  saveTag: async (t: RunTag) =>
    t.id
      ? (await h.put<RunTag>(base + "/catalog/tags/" + t.id, t)).data
      : (await h.post<RunTag>(base + "/catalog/tags", t)).data,
  removeTag: async (id: number) => {
    await h.delete(base + "/catalog/tags/" + id);
  },

  // painel de disparo
  containers: async (todos = false) =>
    (await h.get<RunContainer[]>(base + "/containers", { params: { todos } })).data,

  saveContainer: async (c: RunContainer) =>
    c.id
      ? (await h.put<RunContainer>(base + "/containers/" + c.id, c)).data
      : (await h.post<RunContainer>(base + "/containers", c)).data,
  removeContainer: async (id: number) => {
    await h.delete(base + "/containers/" + id);
  },

  saveCard: async (c: RunCard) =>
    c.id
      ? (await h.put<RunCard>(base + "/cards/" + c.id, c)).data
      : (await h.post<RunCard>(base + "/cards", c)).data,
  removeCard: async (id: number) => {
    await h.delete(base + "/cards/" + id);
  },

  runCard: async (id: number) =>
    (await h.post<RunExecution>(base + "/cards/" + id + "/run")).data,

  // execuções e terminal
  executions: async () => (await h.get<RunExecution[]>(base + "/executions")).data,
  execution: async (id: number) =>
    (await h.get<RunExecution>(base + "/executions/" + id)).data,
  /** Só o que apareceu depois do offset — o log inteiro voltaria a cada segundo. */
  log: async (id: number, offset: number) =>
    (await h.get<RunLog>(base + "/executions/" + id + "/log", { params: { offset } })).data,
  cancel: async (id: number) => {
    await h.post(base + "/executions/" + id + "/cancel");
  },

  // projeto e casos
  project: async (systemId: number) =>
    (await h.get<RunProjectRead>(base + "/project/" + systemId)).data,
  /** Varre o disco: pode levar alguns segundos num projeto grande. */
  scan: async (systemId: number) =>
    (await h.post<RunProjectRead>(base + "/project/" + systemId + "/scan", {}, { timeout: 120000 }))
      .data,
  saveManualCase: async (c: RunManualCase) =>
    c.id
      ? (await h.put<RunManualCase>(base + "/project/cases/" + c.id, c)).data
      : (await h.post<RunManualCase>(base + "/project/cases", c)).data,
  removeManualCase: async (id: number) => {
    await h.delete(base + "/project/cases/" + id);
  },
  runSelection: async (pedido: {
    systemId: number;
    environmentId?: number | null;
    title?: string;
    caseIds: string[];
  }) => (await h.post<RunExecution>(base + "/project/run-selection", pedido)).data,

  // métricas e falhas
  metrics: async (periodo: { de?: string; ate?: string } = {}) =>
    (await h.get<RunMetrics>(base + "/metrics", { params: periodo })).data,
  failures: async (executionId?: number) =>
    (await h.get<RunFailure[]>(base + "/failures", { params: { executionId } })).data,
  vimeoStatus: async () =>
    (await h.get<{ configured: boolean }>(base + "/failures/vimeo-status")).data,
  /** Upload de vídeo demora: o tempo aqui é maior que o padrão de propósito. */
  sendVideo: async (id: number) =>
    (await h.post<{ url: string }>(base + "/failures/" + id + "/vimeo", {}, { timeout: 900000 }))
      .data,
  createIssue: async (
    id: number,
    pedido: {
      projectKey: string;
      issueType?: string;
      summary?: string;
      description?: string;
      enviarVideo: boolean;
    },
  ) =>
    (await h.post<{ key: string }>(base + "/failures/" + id + "/jira", pedido, {
      timeout: 900000,
    })).data,
};

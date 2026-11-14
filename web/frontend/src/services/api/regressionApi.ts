import { httpClient as h } from "./httpClient";
import type { Regression, RegressionItem } from "../../types/regression.types";

/** Período da listagem; em branco, o backend devolve tudo. */
type Periodo = { de?: string; ate?: string };

export const regressionApi = {
  list: async (periodo: Periodo = {}) =>
    (await h.get<Regression[]>("/regressions", { params: periodo })).data,

  get: async (id: number) =>
    (await h.get<Regression>("/regressions/" + id)).data,

  /**
   * Módulos das demandas concluídas no período. Não grava nada: é sugestão para
   * a tela montar a rodada sem digitar módulo por módulo.
   */
  aprovados: async (periodo: Periodo = {}) =>
    (
      await h.get<RegressionItem[]>("/regressions/aprovados", {
        params: periodo,
      })
    ).data,

  save: async (regression: Regression) =>
    regression.id
      ? (await h.put<Regression>("/regressions/" + regression.id, regression))
          .data
      : (await h.post<Regression>("/regressions", regression)).data,

  /** O clone repete os módulos e zera status e QA: é uma rodada nova. */
  clone: async (id: number, mudancas: Partial<Regression> = {}) =>
    (await h.post<Regression>("/regressions/" + id + "/clone", mudancas)).data,

  remove: async (id: number) => {
    await h.delete("/regressions/" + id);
  },
};

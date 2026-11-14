import { httpClient as h } from "./httpClient";
import type { SuiteTemplate } from "../../types/suiteTemplate.types";

export const suiteTemplateApi = {
  list: async () => (await h.get<SuiteTemplate[]>("/suite-templates")).data,
  /** Só os ativos — é o que o seletor dentro da suíte oferece. */
  available: async () =>
    (await h.get<SuiteTemplate[]>("/suite-templates/disponiveis")).data,
  get: async (id: number) =>
    (await h.get<SuiteTemplate>("/suite-templates/" + id)).data,
  save: async (item: Partial<SuiteTemplate>) =>
    item.id
      ? (await h.put<SuiteTemplate>("/suite-templates/" + item.id, item)).data
      : (await h.post<SuiteTemplate>("/suite-templates", item)).data,
  remove: async (id: number) => {
    await h.delete("/suite-templates/" + id);
  },
  /** Transforma uma suíte já montada em template reutilizável. */
  fromSuite: async (suiteId: number, item: Partial<SuiteTemplate>) =>
    (await h.post<SuiteTemplate>("/suite-templates/from-suite/" + suiteId, item))
      .data,
  /** Acrescenta os casos do template à suíte; devolve quantos entraram. */
  importInto: async (templateId: number, suiteId: number) =>
    (
      await h.post<{ imported: number }>(
        "/suite-templates/" + templateId + "/import/" + suiteId,
      )
    ).data,
};

import { httpClient as h } from "./httpClient";
import type { CatalogKind, CatalogItem } from "../../types/catalog.types";
export const catalogApi = {
  list: async (kind: CatalogKind) =>
    (await h.get<CatalogItem[]>("/" + kind)).data,
  save: async (kind: CatalogKind, item: Partial<CatalogItem>) =>
    item.id
      ? (await h.put("/" + kind + "/" + item.id, item)).data
      : (await h.post("/" + kind, item)).data,
  remove: async (kind: CatalogKind, id: number) => {
    await h.delete("/" + kind + "/" + id);
  },
};

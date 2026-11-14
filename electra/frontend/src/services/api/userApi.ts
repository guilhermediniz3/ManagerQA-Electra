import { httpClient as h } from "./httpClient";
import type { UserAccount } from "../../types/user.types";
export const userApi = {
  list: async () => (await h.get<UserAccount[]>("/api/users")).data,
  /**
   * Atualizar manda o registro inteiro, e não um retalho: a matriz de
   * permissões é substituída pelo estado dos checkboxes, e um PATCH parcial
   * deixaria permissão desmarcada continuar valendo.
   */
  save: async (item: Partial<UserAccount>) => {
    if (item.id) return (await h.put<UserAccount>("/api/users/" + item.id, item)).data;
    return (await h.post<UserAccount>("/api/users", item)).data;
  },
  remove: async (id: number) => {
    await h.delete("/api/users/" + id);
  },
};

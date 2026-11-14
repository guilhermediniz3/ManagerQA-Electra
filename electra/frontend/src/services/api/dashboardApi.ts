import { httpClient as h } from "./httpClient";
import type { Dashboard } from "../../types/dashboard.types";
export const dashboardApi = {
  get: async (params: Record<string, string>) =>
    (await h.get<Dashboard>("/api/dashboard", { params })).data,
};

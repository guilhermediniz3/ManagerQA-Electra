import { httpClient } from "./httpClient";
import type { AuditPage } from "../../types/audit.types";
export const auditApi = {
  list: async (
    page: number,
    action: string | null,
    entityType: string | null,
    size = 25,
  ) =>
    (
      await httpClient.get<AuditPage>("/api/audit-logs", {
        params: {
          page,
          size,
          action: action || undefined,
          entityType: entityType || undefined,
        },
      })
    ).data,
};

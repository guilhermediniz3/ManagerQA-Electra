export interface AuditEntry {
  id: number;
  actorId: number | null;
  actorName: string;
  actorEmail: string;
  action: "CREATE" | "UPDATE" | "DELETE";
  entityType: string;
  entityId: string;
  occurredAt: string;
}
export interface AuditPage {
  content: AuditEntry[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

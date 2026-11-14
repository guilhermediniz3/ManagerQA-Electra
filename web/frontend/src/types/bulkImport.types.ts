import type { JiraSearch } from "./jira.types";

export interface BulkImportProgress {
  phase: "searching" | "importing" | "completed" | "cancelled" | "failed";
  pages: number;
  found: number;
  processed: number;
  imported: number;
  failed: number;
  currentKey?: string;
  error?: string;
  failures: { key: string; message: string }[];
}

export interface BulkImportPorts {
  search: (jql: string, cursor?: string) => Promise<JiraSearch>;
  sync: (key: string) => Promise<unknown>;
}

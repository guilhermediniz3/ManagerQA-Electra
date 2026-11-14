import type { StoredAiSummary } from "./ai.types";
export interface Demand {
  jiraKey: string;
  summary: string;
  description: string;
  statusId: string;
  statusName: string;
  assigneeName: string | null;
  assigneeAccountId: string | null;
  projectKey: string;
  squad: string | null;
  sprint: string | null;
  jiraUpdated: string;
  syncedAt: string;
  testerId: number | null;
  testerName: string | null;
  testerOverride: boolean;
  moduleId: number | null;
  moduleName: string | null;
  technologyId: number | null;
  technologyName: string | null;
  callNumber: string | null;
  testPlanId: number | null;
  aiSummary: StoredAiSummary | null;
}
export interface DemandLocal {
  testerId: number | null;
  testerOverride: boolean;
  moduleId: number | null;
  technologyId: number | null;
  callNumber: string | null;
  testPlanId: number | null;
}

import type { CaseStatus } from "./testCase.types";

/**
 * Uma linha da regressão: um módulo a revisar.
 *
 * O QA é por linha, e não da regressão inteira, porque numa rodada cada módulo
 * costuma cair para um tester diferente.
 */
export interface RegressionItem {
  id?: number;
  systemModuleId?: number | null;
  systemModuleName?: string | null;
  description?: string;
  observation?: string;
  flow?: string;
  status?: CaseStatus | null;
  testerId?: number | null;
  testerName?: string | null;
  jiraKey?: string | null;
  position?: number | null;
}

/** Uma rodada de regressão. */
export interface Regression {
  id?: number;
  name: string;
  data?: string | null;
  observation?: string | null;
  createdBy?: string | null;
  items: RegressionItem[];
  totalItems?: number;
  totalConcluidos?: number;
  totalPendentes?: number;
}

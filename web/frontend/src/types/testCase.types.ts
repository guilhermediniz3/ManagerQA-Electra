export type CaseStatus =
  "EM_PROGRESSO" | "CONCLUIDA" | "IMPEDIMENTO" | "RETORNO";
export interface TestCase {
  id: number;
  codeCase: number;
  scenario: string;
  expectedResult: string;
  obtainedResult: string;
  videoEvidence: string;
  status: CaseStatus;
  data: string;
  testSuiteId: number;
}
export interface CaseDraft {
  scenario: string;
  expectedResult: string;
}

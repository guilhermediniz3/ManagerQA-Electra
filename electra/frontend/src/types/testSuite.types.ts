export interface TestSuite {
  id: number;
  codeSuite: number;
  status: string;
  data: string;
  testPlanId: number;
  testCaseId: number[];
}

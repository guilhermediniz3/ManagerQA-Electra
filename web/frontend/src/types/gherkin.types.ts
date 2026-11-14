export interface GherkinFeature {
  id?: number;
  name: string;
  /** Texto puro: em Gherkin a indentação faz parte da sintaxe. */
  content?: string | null;
  testPlanId?: number | null;
  /** Suíte que originou o arquivo, quando veio de uma conversão. */
  suiteId?: number | null;
  createdBy?: string | null;
  data?: string | null;
  /** Contado pelo servidor; um arquivo ainda não salvo não tem. */
  totalCenarios?: number;
}

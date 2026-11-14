export interface TemplateCase {
  id?: number;
  codeCase?: number | null;
  scenario: string;
  expectedResult: string;
  position?: number | null;
}

export interface SuiteTemplate {
  id?: number;
  name: string;
  description?: string | null;
  systemModuleId?: number | null;
  systemModuleName?: string | null;
  active: boolean;
  createdBy?: string | null;
  /** Vem do backend como dd/MM/yyyy, igual às outras datas do projeto. */
  data?: string | null;
  cases: TemplateCase[];
  /** Preenchido na listagem, onde os casos não são carregados. */
  totalCases: number;
}

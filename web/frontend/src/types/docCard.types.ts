export interface DocCard {
  id?: number;
  title: string;
  description?: string | null;
  /** Nome do ícone em react-icons, como no catálogo de cards que serviu de base. */
  icon?: string | null;
  /** HTML do editor. Guardado como veio — os nós próprios (badge, alerta, vídeo)
   *  dependem dos atributos data-* para voltar a ser o que eram ao reabrir. */
  content?: string | null;
  active: boolean;
  sortOrder?: number | null;
  systemModuleId?: number | null;
  systemModuleName?: string | null;
  /** Suíte que originou o card, quando veio de uma geração automática. */
  suiteId?: number | null;
  createdBy?: string | null;
  data?: string | null;
}

export interface DocChatSource {
  tipo: "card" | "suite";
  id: number;
  titulo: string;
}

export interface DocChatAnswer {
  answer: string;
  sources: DocChatSource[];
  /** De onde veio a resposta: documentacao, suites ou nenhuma. */
  origem: string;
}

/** Documento gerado a partir de uma suíte de teste. */
export interface SuiteDoc {
  title: string;
  /** Texto puro — é o que se exporta (o .feature do Gherkin, por exemplo). */
  content: string;
  /** O mesmo material em HTML, pronto para o editor. */
  html: string;
  format: "markdown" | "html" | "gherkin";
}

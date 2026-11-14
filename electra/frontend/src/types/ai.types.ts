export interface AiSummary {
  provider: string;
  summary: string;
  points: string[];
  questions: string[];
}
/** Resumo já persistido na demanda. */
export interface StoredAiSummary extends AiSummary {
  savedAt: string | null;
}

/** Um modelo baixado na máquina. */
export interface ModeloLocal {
  name: string;
  size: number;
  emUso: boolean;
}

/** Sugestão de modelo para quem não sabe qual escolher. */
export interface ModeloSugerido {
  name: string;
  label: string;
  description: string;
  gigabytes: number;
}

/** O que a tela precisa saber sobre a IA que roda nesta máquina. */
export interface EstadoDaIaLocal {
  available: boolean;
  suggested: ModeloSugerido[];
  installed: ModeloLocal[];
}

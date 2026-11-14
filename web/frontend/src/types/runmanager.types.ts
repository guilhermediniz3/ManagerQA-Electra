/** Cadastro: o que era constante no painel antigo e virou dado. */
export interface RunVersion {
  id?: number;
  systemId?: number | null;
  name: string;
  key?: string;
  active: boolean;
  position?: number | null;
}

export interface RunSystem {
  id?: number;
  name: string;
  key?: string;
  repository?: string | null;
  branch?: string | null;
  localPath?: string | null;
  /** Comando da seleção de casos; {{casos}} vira a lista de alvos. */
  selectionScript?: string | null;
  /** Variáveis fixas do sistema, uma por linha: CHAVE=valor. */
  variables?: string | null;
  /**
   * Como o comando escolhe a versão; {{versao}} vira o identificador.
   * Em branco, vale --project={{versao}}. "nenhum" desliga.
   */
  versionFlag?: string | null;
  active: boolean;
  position?: number | null;
  versions: RunVersion[];
}

export interface RunEnvironment {
  id?: number;
  name: string;
  key?: string;
  /** Pinta o botão de vermelho e pede confirmação. */
  production: boolean;
  /**
   * O que muda de verdade entre um ambiente e outro, uma por linha.
   * Em branco, vale ENV=<identificador>.
   */
  variables?: string | null;
  active: boolean;
  position?: number | null;
}

export interface RunTag {
  id?: number;
  name: string;
  key?: string;
  description?: string | null;
  active: boolean;
  position?: number | null;
}

export interface RunCatalog {
  systems: RunSystem[];
  environments: RunEnvironment[];
  tags: RunTag[];
}

/** Painel de disparo. */
export interface RunCard {
  id?: number;
  containerId?: number | null;
  title: string;
  environmentId?: number | null;
  environmentName?: string | null;
  production?: boolean;
  systemId?: number | null;
  systemName?: string | null;
  versionId?: number | null;
  versionName?: string | null;
  tagId?: number | null;
  tagName?: string | null;
  script: string;
  active: boolean;
  position?: number | null;
}

export interface RunContainer {
  id?: number;
  title: string;
  description?: string | null;
  active: boolean;
  position?: number | null;
  cards: RunCard[];
}

export interface RunExecution {
  id: number;
  cardId?: number | null;
  title: string;
  script: string;
  environmentKey?: string | null;
  systemKey?: string | null;
  engine?: string | null;
  state: "RUNNING" | "SUCCESS" | "FAILED" | "CANCELLED";
  exitCode?: number | null;
  triggeredBy?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  buildUrl?: string | null;
  durationSeconds?: number | null;
}

/** Trecho do log a partir de onde a tela parou de ler. */
export interface RunLog {
  executionId: number;
  state: string;
  offset: number;
  chunk: string;
  running: boolean;
}

/** Casos de teste: lidos do projeto e cadastrados à mão. */
export interface RunCase {
  id: string;
  code?: string | null;
  title: string;
  file?: string | null;
  line?: number | null;
  suite?: string | null;
  tags: string[];
  manual: boolean;
  script?: string | null;
}

export interface RunModule {
  key: string;
  name: string;
  version?: string | null;
  path?: string | null;
  tags: string[];
  tests: RunCase[];
}

export interface RunProjectRead {
  systemId: number;
  systemName: string;
  projectRoot?: string | null;
  scannedAt?: string | null;
  scannedBy?: string | null;
  totalModules: number;
  totalTests: number;
  modules: RunModule[];
  structure: { path: string; type: string; size: number }[];
}

export interface RunManualCase {
  id?: number;
  systemId: number;
  moduleKey?: string | null;
  moduleName?: string | null;
  version?: string | null;
  code?: string | null;
  title: string;
  script?: string | null;
  tags?: string | null;
  active: boolean;
}

/** Métricas e falhas. */
export interface RunMetrics {
  de: string;
  ate: string;
  resumo: {
    total: number;
    sucesso: number;
    falha: number;
    emAndamento: number;
    taxaDeSucesso: number;
    duracaoMediaSegundos?: number | null;
  };
  modules: { module: string; version: string; falhas: number }[];
  buckets: { bucket: string; total: number }[];
  days: { dia: string; total: number; sucesso: number; falha: number }[];
  ultimas: RunExecution[];
}

export interface RunFailure {
  id: number;
  executionId: number;
  executionTitle: string;
  title: string;
  fullName?: string | null;
  file?: string | null;
  module?: string | null;
  version?: string | null;
  bucket: string;
  statusCode?: number | null;
  summary: string;
  details: string;
  videoPath?: string | null;
  screenshotPath?: string | null;
  vimeoUrl?: string | null;
  jiraKey?: string | null;
}

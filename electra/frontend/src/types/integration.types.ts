/** `ollama` roda na própria máquina e, por isso, não tem credencial. */
export type Provider =
  | "jira"
  | "openai"
  | "claude"
  | "gemini"
  | "groq"
  | "ollama";
export interface Integration {
  provider: Provider;
  baseUrl: string;
  username: string;
  model: string;
  maskedSecret: string;
  status: string;
  squadField: string | null;
  sprintField: string | null;
  testerField: string | null;
  defaultProvider: boolean;
}
export interface IntegrationInput extends Omit<
  Integration,
  "provider" | "maskedSecret" | "status"
> {
  secret?: string;
}

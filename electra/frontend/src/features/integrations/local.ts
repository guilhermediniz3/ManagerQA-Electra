import type { Integration } from "../../types/integration.types";

/**
 * Providers que rodam na própria máquina.
 *
 * Eles não têm credencial: quem serve o modelo é o computador de quem usa. Por
 * isso a pergunta "está pronta para usar?" não pode ser respondida olhando se
 * há chave salva — era o que o app fazia, e o efeito era a IA local sumir das
 * listas de escolha mesmo estando instalada, configurada e marcada como padrão.
 */
export const PROVIDERS_LOCAIS = ["ollama"];

export const ehLocal = (provider: string) => PROVIDERS_LOCAIS.includes(provider);

/** Integração de IA utilizável: a local basta existir, a de nuvem precisa de chave. */
export const iaPronta = (i: Pick<Integration, "provider" | "maskedSecret">) =>
  i.provider !== "jira" && (ehLocal(i.provider) || !!i.maskedSecret);

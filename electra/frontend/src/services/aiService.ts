import { httpClient as h } from "./api/httpClient";

/**
 * Gera conteúdo para o editor de documentação.
 *
 * Devolve HTML porque é o formato que o editor guarda: pedir Markdown obrigaria
 * a converter no caminho, e o texto chegaria ao card como código em vez de
 * formatação. O provider vem do que estiver configurado em Integrações.
 */
export async function generateAIContent(prompt: string): Promise<string> {
  const { data } = await h.post<{ html: string }>("/api/ai/conteudo", {
    description: prompt,
    provider: null,
  });
  return data.html;
}

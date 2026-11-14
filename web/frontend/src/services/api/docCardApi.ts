import { httpClient as h, TEMPO_DA_IA } from "./httpClient";
import type {
  DocCard,
  DocChatAnswer,
  SuiteDoc,
} from "../../types/docCard.types";

/**
 * Um arquivo gerado pelo servidor, com o nome que ele mandou.
 *
 * O cabeçalho traz o nome duas vezes: um `filename` simples, sem acento, e um
 * `filename*` com o nome completo codificado. Ler o primeiro que aparecesse
 * trazia o rótulo codificado para dentro do nome do arquivo salvo, então aqui
 * o completo tem preferência.
 */
function nomeDoCabecalho(cabecalho: string, extensao: string) {
  const completo = /filename\*=UTF-8''([^;]+)/i.exec(cabecalho)?.[1];
  if (completo) {
    try {
      return decodeURIComponent(completo.trim());
    } catch {
      // Nome com escape malformado: cai para o simples, em vez de falhar.
    }
  }
  const simples = /filename="?([^";]+)"?/i.exec(cabecalho)?.[1];
  return simples ? simples.trim() : "documentacao." + extensao;
}

async function baixar(rota: string, extensao: string) {
  const resposta = await h.get<Blob>(rota, { responseType: "blob", timeout: 2 * 60 * 1000 });
  const cabecalho = String(resposta.headers["content-disposition"] ?? "");
  return { arquivo: resposta.data, nome: nomeDoCabecalho(cabecalho, extensao) };
}

export const docCardApi = {
  list: async () => (await h.get<DocCard[]>("/doc-cards")).data,
  get: async (id: number) => (await h.get<DocCard>("/doc-cards/" + id)).data,
  save: async (item: Partial<DocCard>) =>
    item.id
      ? (await h.put<DocCard>("/doc-cards/" + item.id, item)).data
      : (await h.post<DocCard>("/doc-cards", item)).data,
  clone: async (id: number) =>
    (await h.post<DocCard>("/doc-cards/" + id + "/clone")).data,
  remove: async (id: number) => {
    await h.delete("/doc-cards/" + id);
  },
  /**
   * O card em Word ou em PDF.
   *
   * Vem como arquivo binário, com o nome no cabeçalho — é o servidor quem sabe
   * montar o nome a partir do título e da data.
   */
  word: async (id: number) => baixar("/doc-cards/" + id + "/word", "docx"),
  pdf: async (id: number) => baixar("/doc-cards/" + id + "/pdf", "pdf"),

  /** Pergunta ao acervo: documentação primeiro, casos de teste como reserva. */
  ask: async (question: string) =>
    (
      await h.post<DocChatAnswer>(
        "/doc-cards/chat",
        { question, provider: null },
        { timeout: TEMPO_DA_IA },
      )
    ).data,
};

/** As três formas de virar documentação a partir de uma suíte de teste. */
export const suiteDocApi = {
  exact: async (suiteId: number) =>
    (await h.get<SuiteDoc>("/test-suites/" + suiteId + "/doc/exato")).data,
  withAi: async (suiteId: number) =>
    (
      await h.get<SuiteDoc>("/test-suites/" + suiteId + "/doc/ia", {
        timeout: TEMPO_DA_IA,
      })
    ).data,
  gherkin: async (suiteId: number) =>
    (
      await h.get<SuiteDoc>("/test-suites/" + suiteId + "/doc/gherkin", {
        timeout: TEMPO_DA_IA,
      })
    ).data,
};

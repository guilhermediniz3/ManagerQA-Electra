import { httpClient as h } from "./httpClient";
import type { ContagemDeTabela } from "../../types/database.types";

/**
 * Backup, restauração e limpeza do banco.
 *
 * Os três pedidos têm tempo próprio: um banco com histórico de execuções do
 * RunManager gera arquivo grande, e o limite geral de 35 s cortaria a resposta
 * no meio de um trabalho que o servidor já teria começado.
 */
const TEMPO_DO_BANCO = 10 * 60 * 1000;

export const databaseApi = {
  resumo: async () =>
    (await h.get<ContagemDeTabela[]>("/database/summary")).data,

  /** O arquivo vem como texto; quem salva é a tela, com o nome que o servidor mandou. */
  exportar: async () => {
    const resposta = await h.get<Blob>("/database/backup", {
      responseType: "blob",
      timeout: TEMPO_DO_BANCO,
    });
    const cabecalho = String(resposta.headers["content-disposition"] ?? "");
    const nome = /filename="?([^";]+)"?/i.exec(cabecalho)?.[1];
    return { arquivo: resposta.data, nome: nome ?? "qa-manager-backup.sql" };
  },

  importar: async (conteudo: string) =>
    (
      await h.post<{ comandos: number }>(
        "/database/restore",
        { conteudo },
        { timeout: TEMPO_DO_BANCO },
      )
    ).data,

  zerar: async () =>
    (
      await h.post<Record<string, number>>(
        "/database/reset",
        {},
        { timeout: TEMPO_DO_BANCO },
      )
    ).data,

  /** Apaga tudo, inclusive contas: a sessão desta tela morre junto. */
  apagarTudo: async () =>
    (
      await h.post<Record<string, number>>(
        "/database/wipe",
        {},
        { timeout: TEMPO_DO_BANCO },
      )
    ).data,
};

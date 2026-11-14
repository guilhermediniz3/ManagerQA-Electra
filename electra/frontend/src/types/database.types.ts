/** Uma tabela do banco e o que ela tem hoje. */
export type ContagemDeTabela = {
  tabela: string;
  linhas: number;
  /** Se o botão de zerar leva esta tabela: é o que separa dado de trabalho de cadastro. */
  apagadaAoZerar: boolean;
};

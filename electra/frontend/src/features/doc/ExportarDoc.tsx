import { Button, Group } from "@mantine/core";
import { useMutation } from "@tanstack/react-query";
import { notifications } from "@mantine/notifications";
import { docCardApi } from "../../services/api/docCardApi";

/**
 * Os dois botões de exportação de um card.
 *
 * Vivem em dois lugares — na leitura e dentro do card em Cadastros —, e por
 * isso moram aqui: duas cópias do mesmo par de botões divergiriam na primeira
 * correção que alguém fizesse só de um lado.
 *
 * O download é do navegador. O servidor manda o conteúdo e o nome do arquivo, e
 * aqui só se cria o clique que o browser sabe tratar.
 */
export function ExportarDoc({
  id,
  disabled,
  size = "sm",
}: {
  id?: number;
  disabled?: boolean;
  size?: "xs" | "sm" | "md";
}) {
  const baixar = (arquivo: Blob, nome: string) => {
    const endereco = URL.createObjectURL(arquivo);
    const link = document.createElement("a");
    link.href = endereco;
    link.download = nome;
    link.click();
    URL.revokeObjectURL(endereco);
  };

  const word = useMutation({
    mutationFn: () => docCardApi.word(id!),
    onSuccess: ({ arquivo, nome }) => {
      baixar(arquivo, nome);
      notifications.show({ color: "green", message: "Word gerado: " + nome });
    },
    onError: (erro: Error) =>
      notifications.show({ color: "red", title: "Não foi possível gerar o Word", message: erro.message }),
  });

  const pdf = useMutation({
    mutationFn: () => docCardApi.pdf(id!),
    onSuccess: ({ arquivo, nome }) => {
      baixar(arquivo, nome);
      notifications.show({ color: "green", message: "PDF gerado: " + nome });
    },
    onError: (erro: Error) =>
      notifications.show({ color: "red", title: "Não foi possível gerar o PDF", message: erro.message }),
  });

  // Card ainda não salvo não tem o que exportar: o servidor gera a partir do
  // que está gravado, e não do rascunho aberto na tela.
  const indisponivel = disabled || !id;

  return (
    <Group gap="xs" wrap="nowrap">
      <Button
        variant="default"
        size={size}
        disabled={indisponivel}
        loading={word.isPending}
        onClick={() => word.mutate()}
        title={indisponivel ? "Salve o card para exportar" : "Baixar em Word"}
      >
        Exportar Word
      </Button>
      <Button
        variant="default"
        size={size}
        disabled={indisponivel}
        loading={pdf.isPending}
        onClick={() => pdf.mutate()}
        title={indisponivel ? "Salve o card para exportar" : "Baixar em PDF"}
      >
        Exportar PDF
      </Button>
    </Group>
  );
}

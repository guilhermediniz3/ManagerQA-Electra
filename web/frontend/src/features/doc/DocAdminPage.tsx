import { useEffect, useMemo, useState } from "react";
import {
  ActionIcon,
  Badge,
  Button,
  Group,
  Modal,
  Paper,
  Stack,
  Table,
  Text,
  TextInput,
  Title,
  Tooltip,
  useMantineColorScheme,
} from "@mantine/core";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { notifications } from "@mantine/notifications";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Pager } from "../../components/common/Pager";
import { usePaged } from "../../hooks/usePaged";
import { docCardApi } from "../../services/api/docCardApi";
import type { DocCard } from "../../types/docCard.types";
import { DocCardForm } from "./DocCardForm";

/**
 * Cadastro dos cards de documentação.
 *
 * Aqui é lista, não card: quem administra precisa varrer tudo de uma vez, ver o
 * que está fora do ar e agir — e uma grade de cards grandes atrapalha as duas
 * coisas. É também a única tela onde a situação aparece como selo, porque aqui
 * ela é informação de trabalho, e não um rótulo repetindo o óbvio.
 */
export function DocAdminPage() {
  const queryClient = useQueryClient();
  const navegar = useNavigate();
  const [parametros, setParametros] = useSearchParams();
  const { colorScheme } = useMantineColorScheme();
  const dark = colorScheme === "dark";

  const [busca, setBusca] = useState("");
  const [editando, setEditando] = useState<Partial<DocCard> | null>(null);
  const [removendo, setRemovendo] = useState<DocCard | null>(null);

  const cards = useQuery({ queryKey: ["doc-cards"], queryFn: docCardApi.list });

  // Permite chegar direto na edição de um card vindo da leitura.
  const paraEditar = parametros.get("editar");
  useEffect(() => {
    if (!paraEditar) return;
    let ativo = true;
    void docCardApi.get(Number(paraEditar)).then((card) => {
      if (ativo) setEditando(card);
      setParametros({}, { replace: true });
    });
    return () => {
      ativo = false;
    };
  }, [paraEditar, setParametros]);

  const filtrados = useMemo(() => {
    const termo = busca.trim().toLowerCase();
    const lista = cards.data ?? [];
    if (!termo) return lista;
    return lista.filter(
      (card) =>
        card.title.toLowerCase().includes(termo) ||
        (card.description ?? "").toLowerCase().includes(termo) ||
        (card.systemModuleName ?? "").toLowerCase().includes(termo),
    );
  }, [cards.data, busca]);

  const paginado = usePaged(filtrados, 25);
  const atualizar = () =>
    void queryClient.invalidateQueries({ queryKey: ["doc-cards"] });

  const clonar = useMutation({
    mutationFn: (id: number) => docCardApi.clone(id),
    onSuccess: () => {
      notifications.show({
        color: "green",
        message: "Cópia criada, fora do ar até você revisar.",
      });
      atualizar();
    },
  });

  const alternarSituacao = useMutation({
    mutationFn: async (card: DocCard) => {
      const completo = await docCardApi.get(card.id!);
      return docCardApi.save({ ...completo, active: !completo.active });
    },
    onSuccess: (card) => {
      notifications.show({
        color: "green",
        message: card.active ? "Card publicado." : "Card tirado do ar.",
      });
      atualizar();
    },
  });

  const excluir = useMutation({
    mutationFn: () => docCardApi.remove(removendo!.id!),
    onSuccess: () => {
      notifications.show({ color: "green", message: "Card excluído." });
      setRemovendo(null);
      atualizar();
    },
  });

  const abrirEdicao = async (id: number) =>
    setEditando(await docCardApi.get(id));

  return (
    <Stack gap="lg">
      <Group justify="space-between" align="flex-end" wrap="wrap">
        <div>
          <Title order={3}>Cards de documentação</Title>
          <Text size="sm" c="dimmed">
            Criar, editar e publicar. O que estiver no ar aparece em Doc.
          </Text>
        </div>
        <Group gap="sm">
          <TextInput
            placeholder="Buscar por título, descrição ou módulo…"
            value={busca}
            onChange={(e) => setBusca(e.currentTarget.value)}
            w={280}
          />
          <Button variant="default" onClick={() => navegar("/doc")}>
            Ver documentação
          </Button>
          <Button
            onClick={() =>
              setEditando({
                title: "",
                description: "",
                active: true,
                content: "",
              })
            }
          >
            Novo card
          </Button>
        </Group>
      </Group>

      <Paper p="lg" className="surface">
        {cards.isLoading ? (
          <Text size="sm" c="dimmed">
            Carregando…
          </Text>
        ) : filtrados.length === 0 ? (
          <Text size="sm" c="dimmed">
            {busca
              ? "Nenhum card combina com a busca."
              : "Nenhum card ainda. Crie um aqui, ou gere a partir de uma suíte de teste."}
          </Text>
        ) : (
          <>
            <Table striped highlightOnHover>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Título</Table.Th>
                  <Table.Th>Módulo</Table.Th>
                  <Table.Th>Criado por</Table.Th>
                  <Table.Th>Data</Table.Th>
                  <Table.Th>Situação</Table.Th>
                  <Table.Th />
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {paginado.slice.map((card) => (
                  <Table.Tr key={card.id}>
                    <Table.Td>
                      <Text fw={600} size="sm">
                        {card.title}
                      </Text>
                      {card.description && (
                        <Text size="xs" c="dimmed" lineClamp={2}>
                          {card.description}
                        </Text>
                      )}
                    </Table.Td>
                    <Table.Td>{card.systemModuleName ?? "—"}</Table.Td>
                    <Table.Td>{card.createdBy ?? "—"}</Table.Td>
                    <Table.Td>{card.data ?? "—"}</Table.Td>
                    <Table.Td>
                      <Tooltip
                        label={
                          card.active ? "Tirar do ar" : "Publicar"
                        }
                        withArrow
                      >
                        <Badge
                          color={card.active ? "green" : "red"}
                          radius="xl"
                          size="lg"
                          style={{ cursor: "pointer" }}
                          onClick={() => alternarSituacao.mutate(card)}
                        >
                          {card.active ? "ATIVO" : "INATIVO"}
                        </Badge>
                      </Tooltip>
                    </Table.Td>
                    <Table.Td>
                      <Group gap="xs" justify="flex-end" wrap="nowrap">
                        <Tooltip label="Ler" withArrow>
                          <ActionIcon
                            radius="xl"
                            size="lg"
                            color="gray"
                            variant={dark ? "light" : "filled"}
                            onClick={() => navegar("/doc/" + card.id)}
                          >
                            👁
                          </ActionIcon>
                        </Tooltip>
                        <Tooltip label="Editar" withArrow>
                          <ActionIcon
                            radius="xl"
                            size="lg"
                            color="blue"
                            variant={dark ? "light" : "filled"}
                            onClick={() => void abrirEdicao(card.id!)}
                          >
                            ✎
                          </ActionIcon>
                        </Tooltip>
                        <Tooltip label="Duplicar" withArrow>
                          <ActionIcon
                            radius="xl"
                            size="lg"
                            color="violet"
                            variant={dark ? "light" : "filled"}
                            loading={
                              clonar.isPending && clonar.variables === card.id
                            }
                            onClick={() => clonar.mutate(card.id!)}
                          >
                            ⧉
                          </ActionIcon>
                        </Tooltip>
                        <Tooltip label="Excluir" withArrow>
                          <ActionIcon
                            radius="xl"
                            size="lg"
                            color="red"
                            variant={dark ? "light" : "filled"}
                            onClick={() => setRemovendo(card)}
                          >
                            ×
                          </ActionIcon>
                        </Tooltip>
                      </Group>
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
            <Pager
              page={paginado.page}
              pages={paginado.pages}
              size={paginado.size}
              total={paginado.total}
              from={paginado.from}
              to={paginado.to}
              onPage={paginado.setPage}
              onSize={paginado.setSize}
              unit="cards"
            />
          </>
        )}
      </Paper>

      {editando && (
        <DocCardForm
          card={editando}
          onClose={() => setEditando(null)}
          onSaved={() => {
            setEditando(null);
            atualizar();
          }}
        />
      )}

      <Modal
        opened={!!removendo}
        onClose={() => setRemovendo(null)}
        title="Excluir card"
      >
        <Stack gap="md">
          <Text size="sm">
            Excluir <strong>{removendo?.title}</strong>? O conteúdo se perde — se
            a ideia é só tirar do ar, clique no selo de situação.
          </Text>
          <Group justify="flex-end" gap="sm">
            <Button variant="subtle" onClick={() => setRemovendo(null)}>
              Cancelar
            </Button>
            <Button
              color="red"
              loading={excluir.isPending}
              onClick={() => excluir.mutate()}
            >
              Excluir
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  );
}

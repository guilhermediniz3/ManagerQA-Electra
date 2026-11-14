import { useState } from "react";
import {
  ActionIcon,
  Badge,
  Button,
  Code,
  Group,
  Modal,
  Paper,
  ScrollArea,
  Stack,
  Table,
  Text,
  TextInput,
  Textarea,
  Tooltip,
} from "@mantine/core";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { notifications } from "@mantine/notifications";
import { gherkinApi } from "../../services/api/gherkinApi";
import type { GherkinFeature } from "../../types/gherkin.types";

/**
 * Arquivos de Gherkin do plano.
 *
 * Ficam aqui, ao lado das suítes, e não na documentação: documentação se lê,
 * Gherkin se executa. Quem procura um `.feature` não devia ter que garimpá-lo
 * no meio de material didático.
 */
export function GherkinPanel({ planId }: { planId: number }) {
  const queryClient = useQueryClient();
  const [aberto, setAberto] = useState<GherkinFeature | null>(null);
  const [editando, setEditando] = useState(false);
  const [removendo, setRemovendo] = useState<GherkinFeature | null>(null);

  const features = useQuery({
    queryKey: ["gherkin", planId],
    queryFn: () => gherkinApi.byPlan(planId),
  });

  const atualizar = () =>
    void queryClient.invalidateQueries({ queryKey: ["gherkin", planId] });

  const salvar = useMutation({
    mutationFn: () => gherkinApi.save(aberto!),
    onSuccess: () => {
      notifications.show({ color: "green", message: "Arquivo salvo." });
      setEditando(false);
      atualizar();
    },
  });

  const excluir = useMutation({
    mutationFn: () => gherkinApi.remove(removendo!.id!),
    onSuccess: () => {
      notifications.show({ color: "green", message: "Arquivo excluído." });
      setRemovendo(null);
      atualizar();
    },
  });

  const abrir = async (id: number, paraEditar: boolean) => {
    setAberto(await gherkinApi.get(id));
    setEditando(paraEditar);
  };

  const baixar = (feature: GherkinFeature) => {
    const nome =
      feature.name
        .replace(/[^\p{L}\p{N}]+/gu, "-")
        .replace(/^-|-$/g, "")
        .toLowerCase() || "cenarios";
    const arquivo = new Blob([feature.content ?? ""], {
      type: "text/plain;charset=utf-8",
    });
    const url = URL.createObjectURL(arquivo);
    const link = document.createElement("a");
    link.href = url;
    link.download = nome + ".txt";
    link.click();
    URL.revokeObjectURL(url);
  };

  const baixarPorId = async (id: number) => baixar(await gherkinApi.get(id));

  return (
    <Stack gap="lg">
      <Group justify="space-between" align="flex-end" wrap="wrap">
        <Text size="sm" c="dimmed">
          Cenários em Gherkin deste plano. Converta uma suíte pelo botão
          “Converter para Gherkin”, dentro dela.
        </Text>
        <Button
          size="xs"
          variant="default"
          onClick={() => {
            setAberto({
              name: "",
              content: "Funcionalidade: \n\n  Cenário: \n    Dado \n    Quando \n    Então ",
              testPlanId: planId,
            });
            setEditando(true);
          }}
        >
          Novo arquivo
        </Button>
      </Group>

      <Paper p="lg" className="surface">
        {features.isLoading ? (
          <Text size="sm" c="dimmed">
            Carregando…
          </Text>
        ) : (features.data ?? []).length === 0 ? (
          <Text size="sm" c="dimmed">
            Nenhum arquivo ainda. Abra uma suíte e use “Converter para Gherkin”,
            ou crie um aqui.
          </Text>
        ) : (
          <Table striped highlightOnHover>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Arquivo</Table.Th>
                <Table.Th>Cenários</Table.Th>
                <Table.Th>Criado por</Table.Th>
                <Table.Th>Data</Table.Th>
                <Table.Th />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {(features.data ?? []).map((feature) => (
                <Table.Tr key={feature.id}>
                  <Table.Td>
                    <Text fw={600} size="sm">
                      {feature.name}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Badge variant="light">{feature.totalCenarios}</Badge>
                  </Table.Td>
                  <Table.Td>{feature.createdBy ?? "—"}</Table.Td>
                  <Table.Td>{feature.data ?? "—"}</Table.Td>
                  <Table.Td>
                    <Group gap="xs" justify="flex-end" wrap="nowrap">
                      <Button
                        size="xs"
                        variant="default"
                        onClick={() => void abrir(feature.id!, false)}
                      >
                        Abrir
                      </Button>
                      <Tooltip label="Baixar .txt" withArrow>
                        <ActionIcon
                          variant="subtle"
                          onClick={() => void baixarPorId(feature.id!)}
                          aria-label="Baixar"
                        >
                          ↓
                        </ActionIcon>
                      </Tooltip>
                      <Tooltip label="Excluir" withArrow>
                        <ActionIcon
                          variant="subtle"
                          color="red"
                          onClick={() => setRemovendo(feature)}
                          aria-label="Excluir"
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
        )}
      </Paper>

      <Modal
        opened={!!aberto}
        onClose={() => {
          setAberto(null);
          setEditando(false);
        }}
        size="xl"
        title={aberto?.id ? aberto.name : "Novo arquivo Gherkin"}
      >
        {aberto && (
          <Stack gap="md">
            {editando ? (
              <>
                <TextInput
                  label="Nome do arquivo"
                  value={aberto.name}
                  onChange={(e) =>
                    setAberto({ ...aberto, name: e.currentTarget.value })
                  }
                />
                <Textarea
                  label="Conteúdo"
                  description="Texto puro: em Gherkin a indentação faz parte da sintaxe."
                  autosize
                  minRows={14}
                  styles={{ input: { fontFamily: "monospace", fontSize: 13 } }}
                  value={aberto.content ?? ""}
                  onChange={(e) =>
                    setAberto({ ...aberto, content: e.currentTarget.value })
                  }
                />
              </>
            ) : (
              <ScrollArea.Autosize mah={460} type="auto">
                <Code block style={{ whiteSpace: "pre-wrap", fontSize: 13 }}>
                  {aberto.content}
                </Code>
              </ScrollArea.Autosize>
            )}

            {salvar.isError && (
              <Text size="sm" c="red">
                {(salvar.error as Error).message}
              </Text>
            )}

            <Group justify="space-between">
              <Button
                variant="default"
                size="sm"
                onClick={() => baixar(aberto)}
              >
                Baixar .txt
              </Button>
              <Group gap="sm">
                {editando ? (
                  <>
                    <Button
                      variant="subtle"
                      onClick={() =>
                        aberto.id ? setEditando(false) : setAberto(null)
                      }
                    >
                      Cancelar
                    </Button>
                    <Button
                      loading={salvar.isPending}
                      onClick={() => salvar.mutate()}
                    >
                      Salvar
                    </Button>
                  </>
                ) : (
                  <Button onClick={() => setEditando(true)}>Editar</Button>
                )}
              </Group>
            </Group>
          </Stack>
        )}
      </Modal>

      <Modal
        opened={!!removendo}
        onClose={() => setRemovendo(null)}
        title="Excluir arquivo"
      >
        <Stack gap="md">
          <Text size="sm">
            Excluir <strong>{removendo?.name}</strong>? Os cenários se perdem.
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

import { useMemo, useState } from "react";
import {
  ActionIcon,
  Badge,
  Button,
  Group,
  Modal,
  Paper,
  Select,
  Stack,
  Switch,
  Table,
  Text,
  TextInput,
  Textarea,
  Title,
} from "@mantine/core";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { notifications } from "@mantine/notifications";
import { suiteTemplateApi } from "../../services/api/suiteTemplateApi";
import { catalogApi } from "../../services/api/catalogApi";
import type {
  SuiteTemplate,
  TemplateCase,
} from "../../types/suiteTemplate.types";
import { Pager } from "../../components/common/Pager";
import { usePaged } from "../../hooks/usePaged";

const vazio = (): Partial<SuiteTemplate> => ({
  name: "",
  description: "",
  active: true,
  cases: [{ scenario: "", expectedResult: "" }],
});

/**
 * Catálogo de templates de suíte.
 *
 * Um template é o roteiro que se repete de demanda em demanda — só cenário e
 * resultado esperado. Resultado obtido, evidência e status não entram: são
 * preenchidos ao executar, e num molde voltariam como sujeira para limpar a cada
 * importação.
 */
export function TemplatesPage() {
  const queryClient = useQueryClient();
  const [busca, setBusca] = useState("");
  const [editando, setEditando] = useState<Partial<SuiteTemplate> | null>(null);
  const [removendo, setRemovendo] = useState<SuiteTemplate | null>(null);

  const templates = useQuery({
    queryKey: ["suite-templates"],
    queryFn: suiteTemplateApi.list,
  });
  const modules = useQuery({
    queryKey: ["modules"],
    queryFn: () => catalogApi.list("modules"),
  });

  const filtrados = useMemo(() => {
    const termo = busca.trim().toLowerCase();
    const lista = templates.data ?? [];
    if (!termo) return lista;
    return lista.filter(
      (t) =>
        t.name.toLowerCase().includes(termo) ||
        (t.systemModuleName ?? "").toLowerCase().includes(termo),
    );
  }, [templates.data, busca]);

  const paginado = usePaged(filtrados);

  const atualizar = () => {
    void queryClient.invalidateQueries({ queryKey: ["suite-templates"] });
    void queryClient.invalidateQueries({ queryKey: ["suite-templates-ativos"] });
  };

  const salvar = useMutation({
    mutationFn: () => suiteTemplateApi.save(editando!),
    onSuccess: () => {
      notifications.show({ color: "green", message: "Template salvo." });
      setEditando(null);
      atualizar();
    },
  });

  const excluir = useMutation({
    mutationFn: () => suiteTemplateApi.remove(removendo!.id!),
    onSuccess: () => {
      notifications.show({ color: "green", message: "Template excluído." });
      setRemovendo(null);
      atualizar();
    },
  });

  const abrir = async (id: number) => {
    // A listagem não traz os casos; abrir busca o template completo.
    setEditando(await suiteTemplateApi.get(id));
  };

  const mudarCaso = (indice: number, campo: keyof TemplateCase, valor: string) =>
    setEditando((atual) => {
      if (!atual) return atual;
      const casos = [...(atual.cases ?? [])];
      casos[indice] = { ...casos[indice], [campo]: valor };
      return { ...atual, cases: casos };
    });

  return (
    <Stack gap="lg">
      <Group justify="space-between" align="flex-end" wrap="wrap">
        <div>
          <Title order={3}>Templates de suíte</Title>
          <Text size="sm" c="dimmed">
            Roteiros reutilizáveis. Importe um template dentro de qualquer suíte
            de teste, ou transforme uma suíte pronta em template.
          </Text>
        </div>
        <Group gap="sm">
          <TextInput
            placeholder="Buscar por nome ou módulo…"
            value={busca}
            onChange={(e) => setBusca(e.currentTarget.value)}
            w={260}
          />
          <Button onClick={() => setEditando(vazio())}>Novo template</Button>
        </Group>
      </Group>

      <Paper p="lg" className="surface">
        {templates.isLoading ? (
          <Text size="sm" c="dimmed">
            Carregando templates…
          </Text>
        ) : filtrados.length === 0 ? (
          <Text size="sm" c="dimmed">
            {busca
              ? "Nenhum template combina com a busca."
              : "Nenhum template ainda. Crie um aqui ou envie uma suíte de teste para template."}
          </Text>
        ) : (
          <>
            <Table striped highlightOnHover>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Nome</Table.Th>
                  <Table.Th>Módulo</Table.Th>
                  <Table.Th>Casos</Table.Th>
                  <Table.Th>Criado por</Table.Th>
                  <Table.Th>Data</Table.Th>
                  <Table.Th>Situação</Table.Th>
                  <Table.Th />
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {paginado.slice.map((template) => (
                  <Table.Tr key={template.id}>
                    <Table.Td>
                      <Text fw={600} size="sm">
                        {template.name}
                      </Text>
                      {template.description && (
                        <Text size="xs" c="dimmed" lineClamp={2}>
                          {template.description}
                        </Text>
                      )}
                    </Table.Td>
                    <Table.Td>{template.systemModuleName ?? "—"}</Table.Td>
                    <Table.Td>{template.totalCases}</Table.Td>
                    <Table.Td>{template.createdBy ?? "—"}</Table.Td>
                    <Table.Td>{template.data ?? "—"}</Table.Td>
                    <Table.Td>
                      <Badge
                        variant="light"
                        color={template.active ? "teal" : "gray"}
                      >
                        {template.active ? "Ativo" : "Inativo"}
                      </Badge>
                    </Table.Td>
                    <Table.Td>
                      <Group gap={6} justify="flex-end" wrap="nowrap">
                        <Button
                          size="xs"
                          variant="default"
                          onClick={() => void abrir(template.id!)}
                        >
                          Abrir
                        </Button>
                        <ActionIcon
                          variant="subtle"
                          color="red"
                          aria-label="Excluir template"
                          onClick={() => setRemovendo(template)}
                        >
                          ×
                        </ActionIcon>
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
              unit="templates"
            />
          </>
        )}
      </Paper>

      <Modal
        opened={!!editando}
        onClose={() => setEditando(null)}
        size="xl"
        title={editando?.id ? "Editar template" : "Novo template"}
      >
        {editando && (
          <Stack gap="md">
            <TextInput
              label="Nome"
              placeholder="Ex.: Emissão de NF-e — roteiro padrão"
              value={editando.name ?? ""}
              onChange={(e) =>
                setEditando({ ...editando, name: e.currentTarget.value })
              }
            />
            <Textarea
              label="Descrição"
              placeholder="Quando usar este template"
              autosize
              minRows={2}
              value={editando.description ?? ""}
              onChange={(e) =>
                setEditando({ ...editando, description: e.currentTarget.value })
              }
            />
            <Group gap="md" align="flex-end" grow>
              <Select
                label="Módulo"
                placeholder="Sem módulo específico"
                clearable
                searchable
                data={(modules.data ?? []).map((m) => ({
                  value: String(m.id),
                  label: m.name,
                }))}
                value={
                  editando.systemModuleId ? String(editando.systemModuleId) : null
                }
                onChange={(valor) =>
                  setEditando({
                    ...editando,
                    systemModuleId: valor ? Number(valor) : null,
                  })
                }
              />
              <Switch
                label="Ativo"
                description="Só templates ativos aparecem no seletor da suíte"
                checked={editando.active ?? true}
                onChange={(e) =>
                  setEditando({ ...editando, active: e.currentTarget.checked })
                }
              />
            </Group>

            <Group justify="space-between" align="center">
              <Text fw={600} size="sm">
                Casos de teste
              </Text>
              <Button
                size="xs"
                variant="default"
                onClick={() =>
                  setEditando({
                    ...editando,
                    cases: [
                      ...(editando.cases ?? []),
                      { scenario: "", expectedResult: "" },
                    ],
                  })
                }
              >
                Adicionar caso
              </Button>
            </Group>

            <Stack gap="sm">
              {(editando.cases ?? []).map((caso, indice) => (
                <Paper key={indice} p="sm" withBorder>
                  <Group gap="xs" align="flex-start" wrap="nowrap">
                    <Text size="xs" c="dimmed" w={28} mt={8}>
                      {indice + 1}
                    </Text>
                    <Stack gap="xs" style={{ flex: 1 }}>
                      <Textarea
                        label="Cenário"
                        autosize
                        minRows={1}
                        value={caso.scenario ?? ""}
                        onChange={(e) =>
                          mudarCaso(indice, "scenario", e.currentTarget.value)
                        }
                      />
                      <Textarea
                        label="Resultado esperado"
                        autosize
                        minRows={1}
                        value={caso.expectedResult ?? ""}
                        onChange={(e) =>
                          mudarCaso(
                            indice,
                            "expectedResult",
                            e.currentTarget.value,
                          )
                        }
                      />
                    </Stack>
                    <ActionIcon
                      variant="subtle"
                      color="red"
                      mt={28}
                      aria-label="Remover caso"
                      onClick={() =>
                        setEditando({
                          ...editando,
                          cases: (editando.cases ?? []).filter(
                            (_, i) => i !== indice,
                          ),
                        })
                      }
                    >
                      ×
                    </ActionIcon>
                  </Group>
                </Paper>
              ))}
            </Stack>

            <Text size="xs" c="dimmed">
              Resultado obtido, evidência e status não fazem parte do template —
              são preenchidos ao executar a suíte.
            </Text>

            <Group justify="flex-end" gap="sm">
              <Button variant="subtle" onClick={() => setEditando(null)}>
                Cancelar
              </Button>
              <Button
                loading={salvar.isPending}
                onClick={() => salvar.mutate()}
              >
                Salvar template
              </Button>
            </Group>
            {salvar.isError && (
              <Text size="sm" c="red">
                {(salvar.error as Error).message}
              </Text>
            )}
          </Stack>
        )}
      </Modal>

      <Modal
        opened={!!removendo}
        onClose={() => setRemovendo(null)}
        title="Excluir template"
      >
        <Stack gap="md">
          <Text size="sm">
            Excluir <strong>{removendo?.name}</strong>? As suítes que já
            importaram este template não são afetadas — os casos já copiados
            continuam onde estão.
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

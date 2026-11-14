import { useState } from "react";
import {
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
} from "@mantine/core";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { notifications } from "@mantine/notifications";
import { useNavigate } from "react-router-dom";
import { regressionApi } from "../../services/api/regressionApi";
import type { Regression } from "../../types/regression.types";
import { Pager } from "../../components/common/Pager";
import { usePaged } from "../../hooks/usePaged";
import { today, toApiDate, toInputDate } from "../../utils/dates";

/**
 * Rodadas de regressão.
 *
 * A listagem é por data porque é assim que se procura uma rodada: "a de antes
 * da virada do mês", "a da semana passada". O clone está aqui, e não só dentro
 * da rodada, porque começar a próxima a partir da anterior é o caminho normal —
 * o roteiro de módulos muda pouco de uma para outra.
 */
export function RegressionPage() {
  const queryClient = useQueryClient();
  const navegar = useNavigate();
  const [de, setDe] = useState("");
  const [ate, setAte] = useState("");
  const [criando, setCriando] = useState<Partial<Regression> | null>(null);
  const [removendo, setRemovendo] = useState<Regression | null>(null);

  const periodo = {
    de: toApiDate(de) || undefined,
    ate: toApiDate(ate) || undefined,
  };

  const regressoes = useQuery({
    queryKey: ["regressions", periodo.de, periodo.ate],
    queryFn: () => regressionApi.list(periodo),
  });

  const atualizar = () =>
    void queryClient.invalidateQueries({ queryKey: ["regressions"] });

  const criar = useMutation({
    mutationFn: () =>
      regressionApi.save({
        name: criando!.name!.trim(),
        data: criando!.data,
        observation: criando!.observation,
        items: [],
      }),
    onSuccess: (criada) => {
      setCriando(null);
      atualizar();
      navegar("/regressao/" + criada.id);
    },
  });

  const clonar = useMutation({
    mutationFn: (id: number) => regressionApi.clone(id),
    onSuccess: (copia) => {
      notifications.show({
        color: "green",
        title: "Rodada criada a partir da anterior",
        message: "Os módulos vieram junto; status e QA começam em branco.",
      });
      atualizar();
      navegar("/regressao/" + copia.id);
    },
  });

  const excluir = useMutation({
    mutationFn: () => regressionApi.remove(removendo!.id!),
    onSuccess: () => {
      setRemovendo(null);
      atualizar();
    },
  });

  const lista = regressoes.data ?? [];
  const paginado = usePaged(lista);

  return (
    <Stack gap="lg">
      <Group justify="space-between" align="flex-end" wrap="wrap">
        <Title order={2}>Regressão</Title>
        <Button
          onClick={() =>
            setCriando({ name: "", data: today(), observation: "" })
          }
        >
          Nova rodada
        </Button>
      </Group>

      <Paper p="md" className="surface">
        <Group gap="md" align="flex-end" wrap="wrap">
          <TextInput
            type="date"
            label="De"
            value={toInputDate(de)}
            onChange={(e) => setDe(toApiDate(e.target.value))}
          />
          <TextInput
            type="date"
            label="Até"
            value={toInputDate(ate)}
            onChange={(e) => setAte(toApiDate(e.target.value))}
          />
          {(de || ate) && (
            <Button
              variant="subtle"
              onClick={() => {
                setDe("");
                setAte("");
              }}
            >
              Limpar período
            </Button>
          )}
        </Group>
      </Paper>

      <Paper p="lg" className="surface">
        {regressoes.isLoading ? (
          <Text size="sm" c="dimmed">
            Carregando…
          </Text>
        ) : lista.length === 0 ? (
          <Text size="sm" c="dimmed">
            Nenhuma rodada neste período. Crie uma e busque os módulos das
            demandas concluídas.
          </Text>
        ) : (
          <>
            <Table striped highlightOnHover>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Rodada</Table.Th>
                  <Table.Th>Data</Table.Th>
                  <Table.Th>Módulos</Table.Th>
                  <Table.Th>Andamento</Table.Th>
                  <Table.Th visibleFrom="lg">Criada por</Table.Th>
                  <Table.Th />
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {paginado.slice.map((r) => (
                  <Table.Tr key={r.id}>
                    <Table.Td>
                      <Text
                        fw={600}
                        size="sm"
                        style={{ cursor: "pointer" }}
                        onClick={() => navegar("/regressao/" + r.id)}
                      >
                        {r.name}
                      </Text>
                      {r.observation && (
                        <Text size="xs" c="dimmed" lineClamp={1}>
                          {r.observation}
                        </Text>
                      )}
                    </Table.Td>
                    <Table.Td>{r.data ?? "—"}</Table.Td>
                    <Table.Td>{r.totalItems ?? 0}</Table.Td>
                    <Table.Td>
                      <Badge
                        variant="light"
                        color={r.totalPendentes ? "yellow" : "teal"}
                      >
                        {r.totalConcluidos ?? 0} de {r.totalItems ?? 0}
                      </Badge>
                    </Table.Td>
                    <Table.Td visibleFrom="lg">{r.createdBy ?? "—"}</Table.Td>
                    <Table.Td>
                      <Group gap="xs" justify="flex-end" wrap="nowrap">
                        <Button
                          size="xs"
                          variant="default"
                          onClick={() => navegar("/regressao/" + r.id)}
                        >
                          Abrir
                        </Button>
                        <Button
                          size="xs"
                          variant="subtle"
                          loading={
                            clonar.isPending && clonar.variables === r.id
                          }
                          onClick={() => clonar.mutate(r.id!)}
                        >
                          Clonar
                        </Button>
                        <Button
                          size="xs"
                          variant="subtle"
                          color="red"
                          onClick={() => setRemovendo(r)}
                        >
                          Excluir
                        </Button>
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
              unit="rodadas"
            />
          </>
        )}
      </Paper>

      <Modal
        opened={!!criando}
        onClose={() => setCriando(null)}
        title="Nova rodada de regressão"
      >
        {criando && (
          <Stack gap="md">
            <TextInput
              label="Nome"
              placeholder="Regressão de setembro"
              required
              value={criando.name ?? ""}
              onChange={(e) =>
                setCriando({ ...criando, name: e.currentTarget.value })
              }
            />
            <TextInput
              type="date"
              label="Data"
              value={toInputDate(criando.data ?? "")}
              onChange={(e) =>
                setCriando({ ...criando, data: toApiDate(e.target.value) })
              }
            />
            <TextInput
              label="Observação"
              value={criando.observation ?? ""}
              onChange={(e) =>
                setCriando({ ...criando, observation: e.currentTarget.value })
              }
            />
            {criar.isError && (
              <Text size="sm" c="red">
                {(criar.error as Error).message}
              </Text>
            )}
            <Group justify="flex-end" gap="sm">
              <Button variant="subtle" onClick={() => setCriando(null)}>
                Cancelar
              </Button>
              <Button
                loading={criar.isPending}
                disabled={!criando.name?.trim()}
                onClick={() => criar.mutate()}
              >
                Criar e montar
              </Button>
            </Group>
          </Stack>
        )}
      </Modal>

      <Modal
        opened={!!removendo}
        onClose={() => setRemovendo(null)}
        title="Excluir rodada"
      >
        <Stack gap="md">
          <Text size="sm">
            Excluir <strong>{removendo?.name}</strong>? Os módulos e o que já foi
            registrado nesta rodada se perdem.
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

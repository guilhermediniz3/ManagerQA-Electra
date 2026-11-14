import { useState } from "react";
import {
  Badge,
  Button,
  Group,
  Paper,
  Stack,
  Table,
  Text,
  Title,
} from "@mantine/core";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { notifications } from "@mantine/notifications";
import { runManagerApi } from "../../services/api/runManagerApi";
import type { RunExecution } from "../../types/runmanager.types";
import { RunTerminal } from "./RunTerminal";

const COR: Record<string, string> = {
  RUNNING: "blue",
  SUCCESS: "teal",
  FAILED: "red",
  CANCELLED: "gray",
};

const ROTULO: Record<string, string> = {
  RUNNING: "em execução",
  SUCCESS: "sucesso",
  FAILED: "falhou",
  CANCELLED: "cancelada",
};

/**
 * Histórico de execuções.
 *
 * Atualiza sozinho enquanto houver algo rodando e para quando tudo termina:
 * buscar de segundo em segundo uma lista parada só gasta bateria e banda.
 */
export function RunHistoryTab() {
  const [aberta, setAberta] = useState<RunExecution | null>(null);
  const queryClient = useQueryClient();

  const execucoes = useQuery({
    queryKey: ["runmanager", "executions"],
    queryFn: runManagerApi.executions,
    refetchInterval: (query) =>
      (query.state.data ?? []).some((e) => e.state === "RUNNING") ? 3000 : false,
  });

  /**
   * Interromper direto da lista: quem disparou no ambiente errado não deveria
   * precisar abrir o terminal para achar o botão.
   */
  const interromper = useMutation({
    mutationFn: (id: number) => runManagerApi.cancel(id),
    onSuccess: () => {
      notifications.show({ color: "yellow", message: "Execução interrompida." });
      void queryClient.invalidateQueries({ queryKey: ["runmanager", "executions"] });
    },
    onError: (erro: Error) =>
      notifications.show({
        color: "red",
        title: "Não foi possível interromper",
        message: erro.message,
      }),
  });

  const lista = execucoes.data ?? [];

  return (
    <Stack gap="lg">
      <div>
        <Title order={4}>Histórico de execuções</Title>
        <Text size="xs" c="dimmed">
          As últimas 100 execuções disparadas pelo painel.
        </Text>
      </div>

      <Paper p="lg" className="surface">
        {lista.length === 0 ? (
          <Text size="sm" c="dimmed">
            Nada foi disparado ainda.
          </Text>
        ) : (
          <Table striped highlightOnHover>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Execução</Table.Th>
                <Table.Th>Estado</Table.Th>
                <Table.Th visibleFrom="md">Ambiente</Table.Th>
                <Table.Th visibleFrom="lg">Quem disparou</Table.Th>
                <Table.Th>Início</Table.Th>
                <Table.Th>Duração</Table.Th>
                <Table.Th />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {lista.map((e) => (
                <Table.Tr key={e.id}>
                  <Table.Td>
                    <Text size="sm" fw={600}>
                      {e.title}
                    </Text>
                    <Text size="10px" c="dimmed" lineClamp={1}>
                      {e.script}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Badge size="sm" variant="light" color={COR[e.state] ?? "gray"}>
                      {ROTULO[e.state] ?? e.state}
                    </Badge>
                  </Table.Td>
                  <Table.Td visibleFrom="md">{e.environmentKey ?? "—"}</Table.Td>
                  <Table.Td visibleFrom="lg">{e.triggeredBy ?? "—"}</Table.Td>
                  <Table.Td>{e.startedAt ?? "—"}</Table.Td>
                  <Table.Td>
                    {e.durationSeconds == null ? "—" : duracao(e.durationSeconds)}
                  </Table.Td>
                  <Table.Td>
                    <Group gap={4} justify="flex-end" wrap="nowrap">
                      {e.state === "RUNNING" && (
                        <Button
                          size="compact-xs"
                          color="red"
                          loading={interromper.isPending && interromper.variables === e.id}
                          onClick={() => interromper.mutate(e.id)}
                        >
                          Interromper
                        </Button>
                      )}
                      <Button size="compact-xs" variant="default" onClick={() => setAberta(e)}>
                        Ver terminal
                      </Button>
                    </Group>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        )}
      </Paper>

      {aberta && (
        <>
          <Group justify="flex-end">
            <Button size="xs" variant="subtle" onClick={() => setAberta(null)}>
              Fechar terminal
            </Button>
          </Group>
          <RunTerminal execucao={aberta} />
        </>
      )}
    </Stack>
  );
}

function duracao(segundos: number) {
  if (segundos < 60) return segundos + "s";
  const minutos = Math.floor(segundos / 60);
  return minutos + "min " + (segundos % 60) + "s";
}

import { useState } from "react";
import {
  Badge,
  Button,
  Group,
  Paper,
  SimpleGrid,
  Stack,
  Table,
  Text,
  TextInput,
  Title,
} from "@mantine/core";
import { useQuery } from "@tanstack/react-query";
import { runManagerApi } from "../../services/api/runManagerApi";
import { toApiDate, toInputDate } from "../../utils/dates";

const BUCKETS: Record<string, { rotulo: string; cor: string }> = {
  REGRA_NEGOCIO: { rotulo: "Regra de negócio", cor: "red" },
  STATUS_CODE: { rotulo: "Resposta do backend", cor: "orange" },
  LOCATOR: { rotulo: "Elemento / locator", cor: "yellow" },
  INFRA: { rotulo: "Infraestrutura", cor: "gray" },
  OUTRO: { rotulo: "Outro", cor: "blue" },
};

/**
 * Métricas do período.
 *
 * A taxa de sucesso conta só o que terminou: incluir o que ainda está rodando
 * faria o número cair a cada disparo e subir sozinho depois, sem nada ter
 * acontecido.
 *
 * A separação por tipo de falha é o que dá utilidade ao resto — uma semana com
 * muitas falhas de locator não diz o mesmo que uma semana com muitas falhas de
 * regra de negócio, e só a segunda é problema do produto.
 */
export function RunMetricsTab() {
  const [de, setDe] = useState("");
  const [ate, setAte] = useState("");

  const metricas = useQuery({
    queryKey: ["runmanager", "metrics", de, ate],
    queryFn: () =>
      runManagerApi.metrics({
        de: toApiDate(de) || undefined,
        ate: toApiDate(ate) || undefined,
      }),
  });

  const dados = metricas.data;
  const maiorModulo = Math.max(1, ...(dados?.modules ?? []).map((m) => m.falhas));

  return (
    <Stack gap="lg">
      <Group justify="space-between" align="flex-end" wrap="wrap">
        <div>
          <Title order={4}>Métricas</Title>
          <Text size="xs" c="dimmed">
            {dados ? "De " + dados.de + " a " + dados.ate : "Carregando…"}
          </Text>
        </div>
        <Group gap="sm" align="flex-end">
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
              Últimos 30 dias
            </Button>
          )}
        </Group>
      </Group>

      <SimpleGrid cols={{ base: 2, md: 4 }} spacing="md">
        <Indicador
          titulo="Execuções com sucesso"
          valor={(dados?.resumo.taxaDeSucesso ?? 0) + "%"}
          detalhe={
            (dados?.resumo.sucesso ?? 0) +
            " de " +
            ((dados?.resumo.sucesso ?? 0) + (dados?.resumo.falha ?? 0)) +
            " concluídas"
          }
          cor="teal"
        />
        <Indicador
          titulo="Execuções com falha"
          valor={String(dados?.resumo.falha ?? 0)}
          detalhe="Código de saída diferente de zero"
          cor="red"
        />
        <Indicador
          titulo="Duração média"
          valor={
            dados?.resumo.duracaoMediaSegundos == null
              ? "—"
              : duracao(dados.resumo.duracaoMediaSegundos)
          }
          detalhe="Do disparo ao fim do processo"
          cor="blue"
        />
        <Indicador
          titulo="Disparos no período"
          valor={String(dados?.resumo.total ?? 0)}
          detalhe={(dados?.resumo.emAndamento ?? 0) + " em execução agora"}
          cor="grape"
        />
      </SimpleGrid>

      <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="md">
        <Paper p="lg" className="surface">
          <Text size="xs" fw={700} tt="uppercase" mb="xs">
            Falhas por módulo
          </Text>
          {(dados?.modules ?? []).length === 0 ? (
            <Text size="sm" c="dimmed">
              Nenhuma falha registrada no período.
            </Text>
          ) : (
            <Stack gap="sm">
              {(dados?.modules ?? []).slice(0, 12).map((m) => (
                <div key={m.version + "/" + m.module}>
                  <Group justify="space-between" gap="xs">
                    <Group gap={6}>
                      <Badge size="xs" variant="outline">
                        {m.version}
                      </Badge>
                      <Text size="sm">{m.module}</Text>
                    </Group>
                    <Text size="xs" c="dimmed">
                      {m.falhas}
                    </Text>
                  </Group>
                  <div className="run-bar-track">
                    <div
                      className="run-bar"
                      style={{ width: Math.round((m.falhas / maiorModulo) * 100) + "%" }}
                    />
                  </div>
                </div>
              ))}
            </Stack>
          )}
        </Paper>

        <Paper p="lg" className="surface">
          <Text size="xs" fw={700} tt="uppercase" mb="xs">
            Tipo de falha
          </Text>
          {(dados?.buckets ?? []).length === 0 ? (
            <Text size="sm" c="dimmed">
              Sem falhas para classificar.
            </Text>
          ) : (
            <Stack gap="xs">
              {(dados?.buckets ?? []).map((b) => (
                <Group key={b.bucket} justify="space-between">
                  <Badge variant="light" color={BUCKETS[b.bucket]?.cor ?? "blue"}>
                    {BUCKETS[b.bucket]?.rotulo ?? b.bucket}
                  </Badge>
                  <Text size="sm" fw={600}>
                    {b.total}
                  </Text>
                </Group>
              ))}
              <Text size="xs" c="dimmed" mt="xs">
                Regra de negócio é defeito do produto. Locator e infraestrutura são
                conserto na automação ou no ambiente.
              </Text>
            </Stack>
          )}
        </Paper>
      </SimpleGrid>

      <Paper p="lg" className="surface">
        <Text size="xs" fw={700} tt="uppercase" mb="xs">
          Últimas execuções
        </Text>
        {(dados?.ultimas ?? []).length === 0 ? (
          <Text size="sm" c="dimmed">
            Nada no período.
          </Text>
        ) : (
          <Table striped>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Execução</Table.Th>
                <Table.Th>Estado</Table.Th>
                <Table.Th>Início</Table.Th>
                <Table.Th>Duração</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {(dados?.ultimas ?? []).map((e) => (
                <Table.Tr key={e.id}>
                  <Table.Td>{e.title}</Table.Td>
                  <Table.Td>
                    <Badge
                      size="sm"
                      variant="light"
                      color={e.state === "SUCCESS" ? "teal" : e.state === "FAILED" ? "red" : "blue"}
                    >
                      {e.state}
                    </Badge>
                  </Table.Td>
                  <Table.Td>{e.startedAt ?? "—"}</Table.Td>
                  <Table.Td>
                    {e.durationSeconds == null ? "—" : duracao(e.durationSeconds)}
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        )}
      </Paper>
    </Stack>
  );
}

function Indicador({
  titulo,
  valor,
  detalhe,
  cor,
}: {
  titulo: string;
  valor: string;
  detalhe: string;
  cor: string;
}) {
  return (
    <Paper p="lg" className="surface">
      <Text size="10px" fw={700} tt="uppercase" c="dimmed">
        {titulo}
      </Text>
      <Text size="28px" fw={700} c={cor} mt={4}>
        {valor}
      </Text>
      <Text size="xs" c="dimmed">
        {detalhe}
      </Text>
    </Paper>
  );
}

function duracao(segundos: number) {
  if (segundos < 60) return segundos + "s";
  const minutos = Math.floor(segundos / 60);
  return minutos + "min " + (segundos % 60) + "s";
}

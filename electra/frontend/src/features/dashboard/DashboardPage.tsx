import { useState } from "react";
import {
  Badge,
  Paper,
  SimpleGrid,
  Text,
  Select,
  TextInput,
  Stack,
  Table,
  Progress,
  Group,
} from "@mantine/core";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid,
} from "recharts";
import { dashboardApi } from "../../services/api/dashboardApi";
import { demandApi } from "../../services/api/demandApi";
import { useResource } from "../../hooks/useResource";
import { PageHeading } from "../../components/common/PageHeading";
import { ResourceState } from "../../components/feedback/ResourceState";
import type { MetricGroup } from "../../types/dashboard.types";
function Chart({ title, data }: { title: string; data: MetricGroup[] }) {
  return (
    <Paper p="lg">
      <Text fw={650} mb="md">
        {title}
      </Text>
      {data.length ? (
        <ResponsiveContainer width="100%" height={230}>
          <BarChart
            data={data}
            layout="vertical"
            margin={{ left: 10, right: 25 }}
          >
            <CartesianGrid strokeDasharray="3 3" horizontal={false} />
            <XAxis type="number" allowDecimals={false} />
            <YAxis
              type="category"
              dataKey="name"
              width={120}
              tick={{ fontSize: 11 }}
            />
            <Tooltip />
            <Bar
              dataKey="value"
              name="Demandas"
              fill="#6366f1"
              radius={[0, 4, 4, 0]}
            />
          </BarChart>
        </ResponsiveContainer>
      ) : (
        <ResourceState empty />
      )}
    </Paper>
  );
}
export function DashboardPage() {
  const [filters, setFilters] = useState<Record<string, string>>({});
  const q = useResource(["dashboard", filters], () =>
    dashboardApi.get(filters),
  );
  const demands = useResource(["demands"], demandApi.list);
  const options = (key: "squad" | "sprint") => [
    ...new Set(
      (demands.data || []).map((d) => d[key]).filter((v): v is string => !!v),
    ),
  ];
  const d = q.data;
  return (
    <>
      <PageHeading
        title="Visão geral"
        subtitle="Qualidade e distribuição do trabalho nas demandas importadas"
      >
        <Badge variant="light">Workspace QA</Badge>
      </PageHeading>
      <SimpleGrid cols={{ base: 1, sm: 2, lg: 4 }} mb="lg">
        <Select
          placeholder="Todas as squads"
          clearable
          data={options("squad")}
          value={filters.squad || null}
          onChange={(v) => setFilters({ ...filters, squad: v || "" })}
        />
        <Select
          placeholder="Todas as sprints"
          clearable
          data={options("sprint")}
          value={filters.sprint || null}
          onChange={(v) => setFilters({ ...filters, sprint: v || "" })}
        />
        <TextInput
          type="date"
          aria-label="Data inicial"
          value={filters.start || ""}
          onChange={(e) => setFilters({ ...filters, start: e.target.value })}
        />
        <TextInput
          type="date"
          aria-label="Data final"
          value={filters.end || ""}
          onChange={(e) => setFilters({ ...filters, end: e.target.value })}
        />
      </SimpleGrid>
      <Text size="xs" c="dimmed" mb="md">
        Período baseado na última atualização do Jira. Aguardando Deploy
        representa aprovação pelo Tester.
      </Text>
      <ResourceState
        loading={q.isLoading}
        error={q.error}
        retry={() => q.refetch()}
      />
      {d && (
        <Stack gap="lg">
          <SimpleGrid cols={{ base: 2, lg: 5 }}>
            {[
              ["Demandas", d.total, "indigo"],
              ["Bloqueadas", d.blocked, "orange"],
              ["Retornos", d.returned, "red"],
              ["Aguardando Deploy", d.awaitingDeploy, "blue"],
              ["Aprovadas", d.approved, "teal"],
            ].map(([label, value, color]) => (
              <Paper p="lg" key={label}>
                <Text size="sm" c="dimmed">
                  {label}
                </Text>
                <Text className="metric-number" c={String(color)}>
                  {value}
                </Text>
              </Paper>
            ))}
          </SimpleGrid>
          <SimpleGrid cols={{ base: 1, lg: 2 }}>
            <Chart title="Distribuição por status" data={d.byStatus} />
            <Chart title="Demandas por developer" data={d.byDeveloper} />
            <Chart title="Demandas por tester" data={d.byTester} />
            <Chart title="Demandas por tecnologia" data={d.byTechnology} />
          </SimpleGrid>
          <Paper p="lg">
            <Group justify="space-between" mb="md">
              <Text fw={700}>Mapa de Saúde dos Módulos</Text>
              <Text size="xs" c="dimmed">
                Retornos / demandas importadas
              </Text>
            </Group>
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Módulo</Table.Th>
                  <Table.Th>Tasks</Table.Th>
                  <Table.Th>Retornos</Table.Th>
                  <Table.Th>Concentração</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {d.modules.map((m) => (
                  <Table.Tr key={m.name}>
                    <Table.Td>{m.name}</Table.Td>
                    <Table.Td>{m.tasks}</Table.Td>
                    <Table.Td>{m.returns}</Table.Td>
                    <Table.Td>
                      <Group wrap="nowrap">
                        <Progress
                          w={140}
                          value={m.concentration}
                          color={m.returns ? "red" : "teal"}
                        />
                        <Text size="xs">{Math.round(m.concentration)}%</Text>
                      </Group>
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
            {!d.modules.length && <ResourceState empty />}
          </Paper>
        </Stack>
      )}
    </>
  );
}

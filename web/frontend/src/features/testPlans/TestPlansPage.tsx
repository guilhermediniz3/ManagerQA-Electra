import { useState } from "react";
import { Link } from "react-router-dom";
import {
  Badge,
  Button,
  Group,
  Paper,
  SegmentedControl,
  Stack,
  Table,
  Text,
  TextInput,
} from "@mantine/core";
import { useMutation } from "@tanstack/react-query";
import { testPlanApi } from "../../services/api/testPlanApi";
import { demandApi } from "../../services/api/demandApi";
import { useResource, useRefresh } from "../../hooks/useResource";
import { usePaged } from "../../hooks/usePaged";
import type { TestPlan } from "../../types/testPlan.types";
import { PageHeading } from "../../components/common/PageHeading";
import { Pager } from "../../components/common/Pager";
import { ResourceState } from "../../components/feedback/ResourceState";
import { ConfirmDelete } from "../../components/common/ConfirmDelete";
import { statusColor, statusLabel } from "../../utils/status";
import { TestPlanForm } from "./TestPlanForm";
import { today } from "../../utils/dates";
type Scope = "testing" | "all";
export function TestPlansPage() {
  const q = useResource(["plans"], testPlanApi.list);
  const demands = useResource(["demands"], demandApi.list);
  const refresh = useRefresh();
  const [search, setSearch] = useState(""),
    [scope, setScope] = useState<Scope>("testing"),
    [edit, setEdit] = useState<Partial<TestPlan> | null>(null),
    [remove, setRemove] = useState<number | null>(null);
  const del = useMutation({
    mutationFn: testPlanApi.remove,
    onSuccess: () => {
      setRemove(null);
      refresh();
    },
  });
  const byPlan = new Map(
    (demands.data || [])
      .filter((d) => d.testPlanId)
      .map((d) => [d.testPlanId as number, d]),
  );
  const plans = q.data || [];
  const rows = plans
    .filter((p) => scope === "all" || byPlan.has(p.id))
    .filter((p) =>
      (p.name + " " + p.jira).toLowerCase().includes(search.toLowerCase()),
    );
  const paged = usePaged(rows);
  return (
    <>
      <PageHeading
        title="Planos de teste"
        subtitle="Demandas em teste, com suas suítes e casos"
      >
        <Button
          variant="default"
          onClick={() =>
            setEdit({
              name: "",
              jira: "",
              status: "EM_PROGRESSO",
              data: today(),
              testeSuiteId: [],
            })
          }
        >
          Novo plano local
        </Button>
      </PageHeading>
      <Paper p="lg" className="surface">
        <Stack gap="lg">
          <Group justify="space-between" wrap="wrap" gap="md">
            <SegmentedControl
              value={scope}
              onChange={(v) => setScope(v as Scope)}
              data={[
                { value: "testing", label: "Demandas em teste" },
                { value: "all", label: "Todos os planos" },
              ]}
            />
            <TextInput
              placeholder="Pesquisar plano ou Jira Key"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              w={{ base: "100%", sm: 280 }}
            />
          </Group>
          <ResourceState
            loading={q.isLoading}
            error={q.error}
            empty={!rows.length}
            retry={() => q.refetch()}
          />
          {!!rows.length && (
            <Table.ScrollContainer minWidth={460} type="native">
              <Table highlightOnHover verticalSpacing="sm" layout="auto">
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th>Plano</Table.Th>
                    <Table.Th visibleFrom="sm">Jira</Table.Th>
                    <Table.Th visibleFrom="lg">Tester</Table.Th>
                    <Table.Th visibleFrom="md">Execução</Table.Th>
                    <Table.Th visibleFrom="lg">Data</Table.Th>
                    <Table.Th w={1}>Ações</Table.Th>
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {paged.slice.map((p) => {
                    const d = byPlan.get(p.id);
                    return (
                      <Table.Tr key={p.id}>
                        <Table.Td className="cell-main">
                          <Text
                            component={Link}
                            to={"/test-plans/" + p.id}
                            fw={700}
                            size="sm"
                            c="indigo"
                          >
                            {p.name}
                          </Text>
                          <Text size="xs" c="dimmed">
                            {d ? d.statusName : "Plano local"}
                          </Text>
                          <Group gap={6} mt={6} hiddenFrom="lg" wrap="wrap">
                            <Badge
                              variant="default"
                              size="sm"
                              fw={500}
                              hiddenFrom="sm"
                            >
                              {p.jira}
                            </Badge>
                            <Badge
                              variant="light"
                              size="sm"
                              color={statusColor(p.status)}
                              hiddenFrom="md"
                            >
                              {statusLabel(p.status)}
                            </Badge>
                            <Badge variant="default" size="sm" fw={500}>
                              {p.data}
                            </Badge>
                            {d?.testerName && (
                              <Badge variant="default" size="sm" fw={500}>
                                {d.testerName}
                              </Badge>
                            )}
                          </Group>
                        </Table.Td>
                        <Table.Td visibleFrom="sm">{p.jira}</Table.Td>
                        <Table.Td visibleFrom="lg">
                          {d?.testerName || "—"}
                        </Table.Td>
                        <Table.Td visibleFrom="md">
                          <Badge variant="light" color={statusColor(p.status)}>
                            {statusLabel(p.status)}
                          </Badge>
                        </Table.Td>
                        <Table.Td visibleFrom="lg">{p.data}</Table.Td>
                        <Table.Td className="cell-action">
                          <Group gap="xs" wrap="nowrap">
                            <Button
                              size="xs"
                              variant="light"
                              component={Link}
                              to={"/test-plans/" + p.id}
                            >
                              Abrir
                            </Button>
                            <Button
                              size="xs"
                              variant="subtle"
                              onClick={() => setEdit(p)}
                            >
                              Editar
                            </Button>
                            <Button
                              size="xs"
                              color="red"
                              variant="subtle"
                              onClick={() => setRemove(p.id)}
                            >
                              Excluir
                            </Button>
                          </Group>
                        </Table.Td>
                      </Table.Tr>
                    );
                  })}
                </Table.Tbody>
              </Table>
            </Table.ScrollContainer>
          )}
          <Pager
            page={paged.page}
            pages={paged.pages}
            size={paged.size}
            total={paged.total}
            from={paged.from}
            to={paged.to}
            onPage={paged.setPage}
            onSize={paged.setSize}
            unit="planos"
          />
        </Stack>
      </Paper>
      {edit && <TestPlanForm initial={edit} onClose={() => setEdit(null)} />}
      <ConfirmDelete
        opened={remove !== null}
        onClose={() => setRemove(null)}
        loading={del.isPending}
        onConfirm={() => remove !== null && del.mutate(remove)}
      />
    </>
  );
}

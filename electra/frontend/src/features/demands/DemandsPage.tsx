import { useState } from "react";
import {
  Badge,
  Button,
  Group,
  Paper,
  SegmentedControl,
  Stack,
  Table,
  Text,
  Tooltip,
} from "@mantine/core";
import { useMutation } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { integrationApi } from "../../services/api/integrationApi";
import { demandApi } from "../../services/api/demandApi";
import { jiraApi } from "../../services/api/jiraApi";
import { useResource, useRefresh } from "../../hooks/useResource";
import { usePaged } from "../../hooks/usePaged";
import { PageHeading } from "../../components/common/PageHeading";
import { Pager } from "../../components/common/Pager";
import { ResourceState } from "../../components/feedback/ResourceState";
import { ConfirmDelete } from "../../components/common/ConfirmDelete";
import { DemandFilters, initialFilters, filterDemands } from "./DemandFilters";
import { JiraImportModal } from "./JiraImportModal";
type Scope = "backlog" | "testing" | "all";
export function DemandsPage() {
  const q = useResource(["demands"], demandApi.list);
  const integrations = useResource(["integrations"], integrationApi.list);
  const statuses = useResource(
    ["jira-statuses"],
    jiraApi.statuses,
    !!integrations.data?.find((i) => i.provider === "jira")?.maskedSecret,
  );
  const [filters, setFilters] = useState(initialFilters),
    [scope, setScope] = useState<Scope>("backlog"),
    [opened, setOpened] = useState(false),
    [sending, setSending] = useState<string | null>(null),
    [remove, setRemove] = useState<string | null>(null);
  const refresh = useRefresh();
  const nav = useNavigate();
  const send = useMutation({
    mutationFn: (key: string) => demandApi.plan(key),
    onSuccess: (updated) => {
      setSending(null);
      refresh();
      if (updated.testPlanId) nav("/test-plans/" + updated.testPlanId);
    },
    onError: () => setSending(null),
  });
  const del = useMutation({
    mutationFn: (key: string) => demandApi.remove(key),
    onSuccess: () => {
      setRemove(null);
      refresh();
    },
  });
  const all = q.data || [];
  const inScope = all.filter((d) =>
    scope === "all"
      ? true
      : scope === "testing"
        ? !!d.testPlanId
        : !d.testPlanId,
  );
  const rows = filterDemands(inScope, filters);
  const paged = usePaged(rows);
  const testing = all.filter((d) => !!d.testPlanId).length;
  return (
    <>
      <PageHeading
        title="Demandas"
        subtitle="Importe do Jira e escolha o que entra em teste"
      >
        <Button onClick={() => setOpened(true)}>Consultar Jira</Button>
      </PageHeading>
      <Paper p="lg" className="surface">
        <Stack gap="lg">
          <Group justify="space-between" wrap="wrap" gap="md">
            <SegmentedControl
              value={scope}
              onChange={(v) => setScope(v as Scope)}
              data={[
                { value: "backlog", label: "Aguardando teste" },
                { value: "testing", label: "Em teste (" + testing + ")" },
                { value: "all", label: "Todas" },
              ]}
            />
            <Text size="sm" c="dimmed">
              {rows.length} de {all.length} demandas
            </Text>
          </Group>
          <DemandFilters
            rows={inScope}
            value={filters}
            onChange={setFilters}
            statuses={(statuses.data || []).map((s) => ({
              value: s.id,
              label: s.name,
            }))}
          />
          <ResourceState
            loading={q.isLoading}
            error={q.error}
            empty={!rows.length}
            retry={() => q.refetch()}
          />
          {!!rows.length && (
            <Table.ScrollContainer minWidth={480} type="native">
              <Table highlightOnHover verticalSpacing="sm" layout="auto">
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th>Demanda</Table.Th>
                    <Table.Th visibleFrom="sm">Status</Table.Th>
                    <Table.Th visibleFrom="lg">Developer</Table.Th>
                    <Table.Th visibleFrom="lg">Tester</Table.Th>
                    <Table.Th visibleFrom="xl">Squad / Sprint</Table.Th>
                    <Table.Th visibleFrom="xl">Módulo / Tecnologia</Table.Th>
                    <Table.Th visibleFrom="md">Atualização</Table.Th>
                    <Table.Th w={1} />
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {paged.slice.map((d) => (
                    <Table.Tr
                      key={d.jiraKey}
                      className="row-link"
                      onClick={() => nav("/demands/" + d.jiraKey)}
                    >
                      <Table.Td className="cell-main">
                        <Text c="indigo" fw={700} size="sm">
                          {d.jiraKey}
                        </Text>
                        <Text size="sm" lineClamp={2}>
                          {d.summary}
                        </Text>
                        <Group gap={6} mt={6} hiddenFrom="xl" wrap="wrap">
                          <Badge variant="light" size="sm" hiddenFrom="sm">
                            {d.statusName}
                          </Badge>
                          {(
                            [
                              ["dev", d.assigneeName, "lg"],
                              ["tester", d.testerName, "lg"],
                              ["squad", d.squad, "xl"],
                              ["module", d.moduleName, "xl"],
                            ] as const
                          )
                            .filter(([, value]) => !!value)
                            .map(([field, value, from]) => (
                              <Badge
                                key={field}
                                variant="default"
                                size="sm"
                                fw={500}
                                hiddenFrom={from}
                              >
                                {value}
                              </Badge>
                            ))}
                        </Group>
                      </Table.Td>
                      <Table.Td visibleFrom="sm">
                        <Badge variant="light" className="badge-wrap">
                          {d.statusName}
                        </Badge>
                      </Table.Td>
                      <Table.Td visibleFrom="lg">
                        {d.assigneeName || "—"}
                      </Table.Td>
                      <Table.Td visibleFrom="lg">
                        {d.testerName || "—"}
                      </Table.Td>
                      <Table.Td visibleFrom="xl">
                        {d.squad || "—"}
                        <Text size="xs" c="dimmed">
                          {d.sprint || "—"}
                        </Text>
                      </Table.Td>
                      <Table.Td visibleFrom="xl">
                        {d.moduleName || "—"}
                        <Text size="xs" c="dimmed">
                          {d.technologyName || "—"}
                        </Text>
                      </Table.Td>
                      <Table.Td visibleFrom="md" className="cell-date">
                        {d.jiraUpdated
                          ? new Date(d.jiraUpdated).toLocaleDateString("pt-BR")
                          : "—"}
                        <Text size="xs" c="dimmed">
                          {d.jiraUpdated
                            ? new Date(d.jiraUpdated).toLocaleTimeString(
                                "pt-BR",
                                { hour: "2-digit", minute: "2-digit" },
                              )
                            : ""}
                        </Text>
                      </Table.Td>
                      <Table.Td
                        className="cell-action"
                        onClick={(e) => e.stopPropagation()}
                      >
                        <Group gap="xs" wrap="nowrap" justify="flex-end">
                          {d.testPlanId ? (
                            <Button
                              size="xs"
                              variant="light"
                              onClick={() => nav("/test-plans/" + d.testPlanId)}
                            >
                              Ver plano
                            </Button>
                          ) : (
                            <Tooltip label="Envia a demanda para Planos de teste">
                              <Button
                                size="xs"
                                loading={sending === d.jiraKey}
                                onClick={() => {
                                  setSending(d.jiraKey);
                                  send.mutate(d.jiraKey);
                                }}
                              >
                                Testar
                              </Button>
                            </Tooltip>
                          )}
                          <Tooltip label="Remove a demanda importada localmente">
                            <Button
                              size="xs"
                              variant="subtle"
                              color="red"
                              onClick={() => setRemove(d.jiraKey)}
                            >
                              Excluir
                            </Button>
                          </Tooltip>
                        </Group>
                      </Table.Td>
                    </Table.Tr>
                  ))}
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
            unit="demandas"
          />
        </Stack>
      </Paper>
      <JiraImportModal opened={opened} onClose={() => setOpened(false)} />
      <ConfirmDelete
        opened={remove !== null}
        onClose={() => setRemove(null)}
        loading={del.isPending}
        onConfirm={() => remove && del.mutate(remove)}
        title={remove ? "Excluir demanda " + remove : "Excluir demanda"}
        confirmLabel="Excluir demanda"
      >
        <Text>
          Remove <b>apenas a demanda importada</b> deste workspace. A issue{" "}
          {remove} no Jira <b>não</b> é alterada, e o plano de testes vinculado
          (com suas suítes e casos) continua em Planos de teste. Para trazê-la
          de volta, use <b>Consultar Jira</b>.
        </Text>
      </ConfirmDelete>
    </>
  );
}

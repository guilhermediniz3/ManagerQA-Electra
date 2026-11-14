import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  Alert,
  Anchor,
  Badge,
  Button,
  Group,
  Paper,
  SimpleGrid,
  Stack,
  Tabs,
  Text,
} from "@mantine/core";
import { testPlanApi } from "../../services/api/testPlanApi";
import { demandApi } from "../../services/api/demandApi";
import { useResource } from "../../hooks/useResource";
import { ResourceState } from "../../components/feedback/ResourceState";
import { PageHeading } from "../../components/common/PageHeading";
import { DemandOverview } from "../demands/DemandOverview";
import { SuitesPanel } from "../testSuites/SuitesPanel";
import { GherkinPanel } from "../gherkin/GherkinPanel";
import { statusColor, statusLabel } from "../../utils/status";
import { TestPlanForm } from "./TestPlanForm";
function Meta({ label, value }: { label: string; value?: string | null }) {
  return (
    <Stack gap={2}>
      <Text
        size="xs"
        c="dimmed"
        tt="uppercase"
        fw={700}
        className="field-label"
      >
        {label}
      </Text>
      <Text size="sm" fw={600}>
        {value || "—"}
      </Text>
    </Stack>
  );
}
export function TestPlanDetailsPage() {
  const { id } = useParams();
  const planId = Number(id);
  const q = useResource(["plan", id], () => testPlanApi.get(planId));
  const demands = useResource(["demands"], demandApi.list);
  const [editing, setEditing] = useState(false);
  const demand = (demands.data || []).find((d) => d.testPlanId === planId);
  return (
    <>
      <ResourceState
        loading={q.isLoading}
        error={q.error}
        retry={() => q.refetch()}
      />
      {q.data && (
        <>
          <Anchor component={Link} to="/test-plans" size="sm" className="crumb">
            ← Planos de teste / {q.data.jira}
          </Anchor>
          <PageHeading
            title={q.data.name}
            subtitle={q.data.jira + " · Plano de testes #" + q.data.id}
          >
            <Group gap="sm">
              <Badge
                size="lg"
                variant="light"
                color={statusColor(q.data.status)}
              >
                {statusLabel(q.data.status)}
              </Badge>
              <Button variant="default" onClick={() => setEditing(true)}>
                Editar plano
              </Button>
            </Group>
          </PageHeading>
          <Paper p="lg" mb="lg" className="surface">
            <SimpleGrid cols={{ base: 2, sm: 3, lg: 5 }} spacing="lg">
              <Meta label="Data" value={q.data.data} />
              <Meta label="Entrega" value={q.data.deliveryData} />
              <Meta label="Chamado" value={q.data.callNumber} />
              <Meta label="Matriz" value={q.data.matriz} />
              <Meta label="Usuário de teste" value={q.data.userName} />
            </SimpleGrid>
          </Paper>
          <Tabs defaultValue="demand" variant="outline" keepMounted={false}>
            <Tabs.List>
              <Tabs.Tab value="demand">Demanda</Tabs.Tab>
              <Tabs.Tab value="suites">Suítes de teste</Tabs.Tab>
              <Tabs.Tab value="gherkin">Gherkin</Tabs.Tab>
            </Tabs.List>
            <Tabs.Panel value="demand" pt="lg">
              {demand ? (
                <DemandOverview
                  key={demand.syncedAt}
                  demand={demand}
                  actions={
                    <Anchor
                      component={Link}
                      to={"/demands/" + demand.jiraKey}
                      size="sm"
                    >
                      Ver esta demanda na tela de Demandas →
                    </Anchor>
                  }
                />
              ) : (
                <Stack gap="lg">
                  <Alert variant="light" title="Plano local sem demanda Jira">
                    Este plano não está vinculado a uma demanda importada do
                    Jira. Use <b>Editar plano</b> para manter os dados ou envie
                    a demanda correspondente pelo botão <b>Testar</b> em
                    Demandas.
                  </Alert>
                  <Paper p="xl" className="surface">
                    <Stack gap="md">
                      <Text fw={750} size="lg">
                        Observações do plano
                      </Text>
                      <Text className="description" size="sm">
                        {q.data.observation || "Sem observações."}
                      </Text>
                    </Stack>
                  </Paper>
                </Stack>
              )}
            </Tabs.Panel>
            <Tabs.Panel value="suites" pt="lg">
              <SuitesPanel
                planId={planId}
                description={
                  demand?.description || q.data.observation || q.data.name
                }
              />
            </Tabs.Panel>
            <Tabs.Panel value="gherkin" pt="lg">
              <GherkinPanel planId={planId} />
            </Tabs.Panel>
          </Tabs>
          {editing && (
            <TestPlanForm initial={q.data} onClose={() => setEditing(false)} />
          )}
        </>
      )}
    </>
  );
}

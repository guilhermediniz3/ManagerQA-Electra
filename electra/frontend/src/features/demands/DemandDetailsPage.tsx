import { useParams, Link, useNavigate } from "react-router-dom";
import { useMutation } from "@tanstack/react-query";
import {
  Alert,
  Anchor,
  Button,
  Group,
  Paper,
  Stack,
  Text,
} from "@mantine/core";
import { demandApi } from "../../services/api/demandApi";
import { useResource, useRefresh } from "../../hooks/useResource";
import { PageHeading } from "../../components/common/PageHeading";
import { ResourceState } from "../../components/feedback/ResourceState";
import type { Demand } from "../../types/demand.types";
import { DemandOverview } from "./DemandOverview";
export function DemandDetailsPage() {
  const { key = "" } = useParams();
  const q = useResource(["demand", key], () => demandApi.get(key));
  return (
    <>
      <ResourceState
        loading={q.isLoading}
        error={q.error}
        retry={() => q.refetch()}
      />
      {q.data && (
        <DemandDetails
          key={
            q.data.syncedAt +
            q.data.testPlanId +
            q.data.testerId +
            q.data.moduleId +
            q.data.technologyId +
            q.data.callNumber
          }
          demand={q.data}
        />
      )}
    </>
  );
}
function DemandDetails({ demand: d }: { demand: Demand }) {
  const refresh = useRefresh();
  const nav = useNavigate();
  const sync = useMutation({
    mutationFn: () => demandApi.sync(d.jiraKey),
    onSuccess: refresh,
  });
  const plan = useMutation({
    mutationFn: () => demandApi.plan(d.jiraKey),
    onSuccess: (updated) => {
      refresh();
      if (updated.testPlanId) nav("/test-plans/" + updated.testPlanId);
    },
  });
  return (
    <>
      <Anchor component={Link} to="/demands" size="sm" className="crumb">
        ← Demandas / {d.jiraKey}
      </Anchor>
      <PageHeading
        title={d.summary}
        subtitle={d.jiraKey + " · " + d.projectKey}
      >
        <Group gap="sm">
          <Button
            variant="default"
            loading={sync.isPending}
            onClick={() => sync.mutate()}
          >
            Sincronizar Jira
          </Button>
          {!d.testPlanId && (
            <Button loading={plan.isPending} onClick={() => plan.mutate()}>
              Testar
            </Button>
          )}
        </Group>
      </PageHeading>
      <DemandOverview
        demand={d}
        actions={
          d.testPlanId ? (
            <Paper p="lg" className="surface highlight">
              <Group justify="space-between" wrap="wrap" gap="md">
                <Stack gap={2}>
                  <Text fw={700}>Esta demanda já está em teste</Text>
                  <Text size="sm" c="dimmed">
                    As suítes e os casos de teste ficam no plano de testes.
                  </Text>
                </Stack>
                <Button
                  component={Link}
                  to={"/test-plans/" + d.testPlanId}
                  variant="light"
                >
                  Abrir plano de testes
                </Button>
              </Group>
            </Paper>
          ) : (
            <Alert variant="light" title="Ainda não está em teste">
              Use <b>Testar</b> para enviar esta demanda para Planos de teste e
              montar as suítes lá.
            </Alert>
          )
        }
      />
    </>
  );
}

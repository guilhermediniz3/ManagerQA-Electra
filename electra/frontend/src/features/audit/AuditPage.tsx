import { useState } from "react";
import {
  Badge,
  Group,
  Paper,
  Select,
  Table,
  Text,
  Button,
} from "@mantine/core";
import { useResource } from "../../hooks/useResource";
import { auditApi } from "../../services/api/auditApi";
import { PageHeading } from "../../components/common/PageHeading";
import { ResourceState } from "../../components/feedback/ResourceState";
import { Pager } from "../../components/common/Pager";
const names: Record<string, string> = {
  Developer: "Desenvolvedor",
  TesterQA: "Tester",
  Technology: "Tecnologia",
  SystemModule: "Módulo",
  TestPlan: "Plano de teste",
  TestSuite: "Suíte",
  TestCaseEntity: "Caso de teste",
  User: "Usuário",
  Demand: "Demanda local",
  IntegrationSetting: "Integração",
};
export function AuditPage() {
  const [page, setPage] = useState(1),
    [size, setSize] = useState(25),
    [action, setAction] = useState<string | null>(null),
    [entity, setEntity] = useState<string | null>(null);
  const q = useResource(["audit", page, size, action, entity], () =>
    auditApi.list(page - 1, action, entity, size),
  );
  const total = q.data?.totalElements || 0;
  return (
    <>
      <PageHeading
        title="Histórico de alterações"
        subtitle="Quem criou, alterou ou excluiu registros no Manager"
      >
        <Button variant="default" onClick={() => q.refetch()}>
          Atualizar
        </Button>
      </PageHeading>
      <Paper p="lg" className="surface">
        <Group mb="lg">
          <Select
            placeholder="Todas as ações"
            clearable
            data={[
              { value: "CREATE", label: "Criação" },
              { value: "UPDATE", label: "Alteração" },
              { value: "DELETE", label: "Exclusão" },
            ]}
            value={action}
            onChange={(v) => {
              setPage(1);
              setAction(v);
            }}
          />
          <Select
            placeholder="Todos os tipos"
            clearable
            searchable
            data={Object.entries(names).map(([value, label]) => ({
              value,
              label,
            }))}
            value={entity}
            onChange={(v) => {
              setPage(1);
              setEntity(v);
            }}
          />
        </Group>
        <ResourceState
          loading={q.isLoading}
          error={q.error}
          empty={!q.data?.content.length}
          retry={() => q.refetch()}
        />
        <Table.ScrollContainer minWidth={420} type="native">
          <Table striped layout="auto">
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Quando</Table.Th>
                <Table.Th visibleFrom="md">Quem</Table.Th>
                <Table.Th>Ação</Table.Th>
                <Table.Th visibleFrom="sm">Registro</Table.Th>
                <Table.Th visibleFrom="lg">Identificação</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {q.data?.content.map((entry) => (
                <Table.Tr key={entry.id}>
                  <Table.Td className="cell-date">
                    {new Date(entry.occurredAt).toLocaleDateString("pt-BR")}
                    <Text size="xs" c="dimmed">
                      {new Date(entry.occurredAt).toLocaleTimeString("pt-BR", {
                        hour: "2-digit",
                        minute: "2-digit",
                      })}
                    </Text>
                    <Text size="xs" c="dimmed" hiddenFrom="md">
                      {entry.actorName || "Sistema"}
                    </Text>
                  </Table.Td>
                  <Table.Td visibleFrom="md">
                    <Text size="sm" fw={600}>
                      {entry.actorName || "Sistema"}
                    </Text>
                    <Text size="xs" c="dimmed">
                      {entry.actorEmail}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Badge
                      color={
                        entry.action === "DELETE"
                          ? "red"
                          : entry.action === "CREATE"
                            ? "teal"
                            : "blue"
                      }
                    >
                      {
                        {
                          CREATE: "Criou",
                          UPDATE: "Alterou",
                          DELETE: "Excluiu",
                        }[entry.action]
                      }
                    </Badge>
                  </Table.Td>
                  <Table.Td visibleFrom="sm">
                    {names[entry.entityType] || entry.entityType}
                  </Table.Td>
                  <Table.Td visibleFrom="lg">{entry.entityId}</Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Table.ScrollContainer>
        <Text size="sm" c="dimmed" mt="lg">
          Sem conteúdo de senhas ou tokens
        </Text>
        <Pager
          page={page}
          pages={Math.max(1, q.data?.totalPages || 1)}
          size={size}
          total={total}
          from={total ? (page - 1) * size + 1 : 0}
          to={Math.min(page * size, total)}
          onPage={setPage}
          onSize={(value) => {
            setSize(value);
            setPage(1);
          }}
          unit="eventos"
        />
      </Paper>
    </>
  );
}

import {
  groupJiraStatuses,
  resolveStatusIds,
} from "../../services/imports/groupJiraStatuses";
import { useBulkDemandImport } from "./useBulkDemandImport";
import { BulkImportStatus } from "./BulkImportStatus";
import { useState } from "react";
import {
  Modal,
  Stack,
  TextInput,
  Button,
  Group,
  Text,
  Select,
  MultiSelect,
  Alert,
} from "@mantine/core";
import { useMutation, useQuery } from "@tanstack/react-query";
import { jiraApi } from "../../services/api/jiraApi";
import { demandApi } from "../../services/api/demandApi";
import { useRefresh } from "../../hooks/useResource";
export function JiraImportModal({
  opened,
  onClose,
}: {
  opened: boolean;
  onClose: () => void;
}) {
  const [jql, setJql] = useState("updated >= -30d ORDER BY updated DESC"),
    [board, setBoard] = useState<string | null>(null);
  const [selectedStatuses, setSelectedStatuses] = useState<string[]>([]);
  const statuses = useQuery({
    queryKey: ["jira-statuses"],
    queryFn: jiraApi.statuses,
    enabled: opened,
    staleTime: 60_000,
  });
  const statusGroups = groupJiraStatuses(statuses.data || []);
  const statusOptions = statusGroups.map(({ value, label }) => ({
    value,
    label,
  }));
  const missingSelection = selectedStatuses.some(
    (value) => !statusGroups.some((group) => group.value === value),
  );
  const refresh = useRefresh();
  const bulk = useBulkDemandImport();
  const search = useMutation({
    mutationFn: (cursor?: string) => jiraApi.search(jql, cursor),
  });
  const boards = useMutation({ mutationFn: jiraApi.boards });
  const sprints = useMutation({ mutationFn: jiraApi.sprints });
  const imp = useMutation({
    mutationFn: demandApi.sync,
    onSuccess: () => refresh(),
  });
  return (
    <Modal
      opened={opened}
      onClose={() => {
        if (!bulk.running) onClose();
      }}
      withCloseButton={!bulk.running}
      closeOnClickOutside={!bulk.running}
      closeOnEscape={!bulk.running}
      title="Consultar e importar issues existentes"
      size="xl"
    >
      <Stack>
        <Alert color="blue">
          Consulte o Jira e importe as demandas que deseja acompanhar neste
          workspace.
        </Alert>
        <Group>
          <Button
            variant="light"
            onClick={() => boards.mutate()}
            loading={boards.isPending}
            disabled={bulk.running || search.isPending}
          >
            Consultar boards
          </Button>
          <Select
            placeholder="Board"
            disabled={bulk.running || search.isPending}
            data={(boards.data?.values || []).map((b) => ({
              value: String(b.id),
              label: b.name,
            }))}
            value={board}
            onChange={(v) => {
              setBoard(v);
              if (v) sprints.mutate(v);
            }}
          />
          <Select
            placeholder="Sprint do board"
            disabled={bulk.running || search.isPending}
            data={(sprints.data?.values || []).map((s) => ({
              value: String(s.id),
              label: s.name,
            }))}
            onChange={(v) =>
              v &&
              (setJql("sprint = " + v + " order by updated DESC"),
              search.reset(),
              bulk.reset())
            }
          />
        </Group>
        <TextInput
          label="Consulta JQL"
          description="A busca padrão considera os últimos 30 dias. Ajuste o período ou filtre por projeto, sprint ou chave."
          value={jql}
          disabled={bulk.running || search.isPending}
          onChange={(e) => {
            setJql(e.target.value);
            search.reset();
            bulk.reset();
          }}
        />
        <Button
          loading={search.isPending}
          disabled={bulk.running || imp.isPending}
          onClick={() => {
            bulk.reset();
            search.mutate(undefined);
          }}
        >
          Consultar Jira
        </Button>
        {search.isSuccess && !!search.data?.issues.length && (
          <Stack gap="xs">
            <MultiSelect
              label="Status para importar em lote"
              description="Cada opção inclui os status com esse nome de todas as squads da consulta. Sem seleção, importa todos os status."
              placeholder={
                statuses.isPending ? "Carregando status…" : "Todos os status"
              }
              searchable
              clearable
              data={statusOptions}
              value={selectedStatuses}
              disabled={
                bulk.running ||
                search.isPending ||
                imp.isPending ||
                statuses.isPending
              }
              onChange={(values) => {
                setSelectedStatuses(values);
                bulk.reset();
              }}
              nothingFoundMessage="Nenhum status encontrado"
            />
            {missingSelection && (
              <Alert color="yellow">
                Os status foram atualizados. Limpe a seleção e selecione
                novamente.
              </Alert>
            )}
            {statuses.isError && (
              <Alert color="red" title="Não foi possível carregar os status">
                <Text size="sm">{statuses.error.message}</Text>
                <Button
                  variant="light"
                  size="xs"
                  loading={statuses.isFetching}
                  onClick={() => void statuses.refetch()}
                >
                  Tentar novamente
                </Button>
              </Alert>
            )}
            <Button
              color="teal"
              loading={bulk.running}
              disabled={
                search.isPending ||
                imp.isPending ||
                statuses.isPending ||
                statuses.isError ||
                missingSelection
              }
              onClick={() =>
                void bulk.start(
                  jql,
                  resolveStatusIds(statusGroups, selectedStatuses),
                )
              }
            >
              {selectedStatuses.length
                ? "Importar todas dos status selecionados"
                : "Importar todas da consulta"}
            </Button>
            <Text size="xs" c="dimmed">
              Inclui todas as páginas desta consulta, respeitando os status
              selecionados. A chave do Jira identifica cada demanda: importar
              novamente atualiza o registro existente, sem duplicar.
            </Text>
          </Stack>
        )}
        {bulk.progress && (
          <BulkImportStatus
            progress={bulk.progress}
            running={bulk.running}
            stopping={bulk.stopping}
            onCancel={bulk.cancel}
          />
        )}
        {search.isError && (
          <Alert color="red" title="Consulta não realizada">
            {search.error.message}
          </Alert>
        )}
        {search.isSuccess && !search.data?.issues.length && (
          <Text c="dimmed">Nenhuma issue encontrada para esta consulta.</Text>
        )}
        {search.data?.issues.map((issue) => (
          <Group justify="space-between" key={issue.key} wrap="nowrap">
            <div>
              <Text fw={600}>
                {issue.key} · {issue.fields.summary}
              </Text>
              <Text c="dimmed" size="sm">
                {issue.fields.status.name} ·{" "}
                {issue.fields.assignee?.displayName || "Sem responsável"}
              </Text>
            </div>
            <Button
              variant="light"
              size="xs"
              loading={imp.isPending}
              disabled={bulk.running || search.isPending}
              onClick={() => imp.mutate(issue.key)}
            >
              Importar / sincronizar
            </Button>
          </Group>
        ))}
        {search.data?.nextPageToken && (
          <Button
            variant="default"
            loading={search.isPending}
            disabled={bulk.running || imp.isPending}
            onClick={() => search.mutate(search.data?.nextPageToken)}
          >
            Próxima página
          </Button>
        )}
      </Stack>
    </Modal>
  );
}

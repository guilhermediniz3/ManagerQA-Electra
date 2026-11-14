import { useMemo, useState } from "react";
import {
  ActionIcon,
  Badge,
  Button,
  Group,
  Paper,
  Stack,
  Text,
  Tooltip,
} from "@mantine/core";
import { useMutation } from "@tanstack/react-query";
import { testSuiteApi } from "../../services/api/testSuiteApi";
import { testCaseApi } from "../../services/api/testCaseApi";
import { useResource, useRefresh } from "../../hooks/useResource";
import { usePaged } from "../../hooks/usePaged";
import { ResourceState } from "../../components/feedback/ResourceState";
import { ConfirmDelete } from "../../components/common/ConfirmDelete";
import { Pager } from "../../components/common/Pager";
import { StatusProgress } from "../../components/common/StatusProgress";
import { CasesPanel } from "../testCases/CasesPanel";
import type { TestSuite } from "../../types/testSuite.types";
import { statusColor, statusLabel, tally } from "../../utils/status";
import { today } from "../../utils/dates";
import { SuiteForm } from "./SuiteForm";
import { SuiteTemplateActions } from "../suiteTemplates/SuiteTemplateActions";
import { SuiteDocActions } from "../suiteTemplates/SuiteDocActions";
function SuiteCard({
  planId,
  suite,
  onOpen,
  onDelete,
}: {
  planId: number;
  suite: TestSuite;
  onOpen: () => void;
  onDelete: () => void;
}) {
  const q = useResource(["cases", suite.id], () =>
    testCaseApi.bySuite(planId, suite.id),
  );
  const t = tally(q.data || []);
  return (
    <Paper
      p="lg"
      role="button"
      tabIndex={0}
      aria-label={"Abrir casos da suíte " + suite.codeSuite}
      onClick={onOpen}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onOpen();
        }
      }}
      className="surface suite-card"
    >
      <Stack gap="md">
        <Group justify="space-between" wrap="nowrap" align="flex-start">
          <Group gap="md" wrap="nowrap">
            <div className="suite-code">
              <Text size="xs" c="dimmed" fw={700}>
                SUÍTE
              </Text>
              <Text fw={800} size="xl" lh={1}>
                {suite.codeSuite}
              </Text>
            </div>
            <Stack gap={4}>
              <Group gap="xs">
                <Badge variant="light" color={statusColor(suite.status)}>
                  {statusLabel(suite.status)}
                </Badge>
                <Text size="sm" c="dimmed">
                  {suite.data}
                </Text>
              </Group>
              <Text size="sm" fw={600}>
                {q.isLoading
                  ? "Carregando casos…"
                  : t.total
                    ? t.total +
                      " casos de teste · " +
                      t.done +
                      " concluídos (" +
                      t.donePercent +
                      "%)"
                    : "Nenhum caso de teste ainda"}
              </Text>
            </Stack>
          </Group>
          <Group gap={6} wrap="nowrap" onClick={(e) => e.stopPropagation()}>
            <Text size="sm" c="dimmed" visibleFrom="md">
              Abrir casos
            </Text>
            <Tooltip label="Excluir suíte e seus casos">
              <ActionIcon
                variant="subtle"
                color="red"
                aria-label={"Excluir suíte " + suite.codeSuite}
                onClick={onDelete}
              >
                <span className="glyph">🗑</span>
              </ActionIcon>
            </Tooltip>
            <span className="glyph suite-chevron" aria-hidden>
              ›
            </span>
          </Group>
        </Group>
        <StatusProgress tally={t} />
      </Stack>
    </Paper>
  );
}
export function SuitesPanel({
  planId,
  description,
}: {
  planId: number;
  description: string;
}) {
  const q = useResource(["suites", planId], () => testSuiteApi.byPlan(planId));
  const [openId, setOpenId] = useState<number | null>(null),
    [remove, setRemove] = useState<number | null>(null),
    [editing, setEditing] = useState(false);
  const refresh = useRefresh();
  const suites = useMemo(
    () => [...(q.data || [])].sort((a, b) => a.codeSuite - b.codeSuite),
    [q.data],
  );
  const open = suites.find((s) => s.id === openId) || null;
  const paged = usePaged(suites);
  const input = () => ({
    testPlanId: planId,
    codeSuite: Math.max(0, ...suites.map((s) => s.codeSuite || 0)) + 1,
    status: "EM_PROGRESSO",
    data: today(),
    testCaseId: [],
  });
  const add = useMutation({
    mutationFn: () => testSuiteApi.save(input()),
    onSuccess: (s) => {
      setOpenId(s.id);
      refresh();
    },
  });
  const clone = useMutation({
    mutationFn: () => testSuiteApi.clone(open!.id, input()),
    onSuccess: (s) => {
      setOpenId(s.id);
      refresh();
    },
  });
  const del = useMutation({
    mutationFn: () => testSuiteApi.remove(remove!),
    onSuccess: () => {
      if (remove === openId) setOpenId(null);
      setRemove(null);
      refresh();
    },
  });
  const removing = suites.find((s) => s.id === remove) || null;
  const confirm = (
    <ConfirmDelete
      opened={remove !== null}
      onClose={() => setRemove(null)}
      loading={del.isPending}
      onConfirm={() => del.mutate()}
      title={removing ? "Excluir suíte " + removing.codeSuite : "Excluir suíte"}
      confirmLabel="Excluir suíte"
    >
      <Text>
        A suíte{removing ? " " + removing.codeSuite : ""} e{" "}
        <b>todos os casos de teste dentro dela</b> serão excluídos. O plano de
        testes e as outras suítes não são afetados.
      </Text>
    </ConfirmDelete>
  );
  if (open)
    return (
      <Stack gap="lg">
        <Group justify="space-between" wrap="wrap" gap="md">
          <Group gap="md">
            <Button variant="subtle" onClick={() => setOpenId(null)}>
              ← Todas as suítes
            </Button>
            <Text fw={800} size="lg">
              Suíte {open.codeSuite}
            </Text>
            <Badge variant="light" color={statusColor(open.status)}>
              {statusLabel(open.status)}
            </Badge>
            <Text size="sm" c="dimmed">
              {open.data}
            </Text>
          </Group>
          <Group gap="xs">
            <Button
              variant="default"
              size="xs"
              loading={clone.isPending}
              onClick={() => clone.mutate()}
            >
              Clonar suíte
            </Button>
            <Button
              variant="default"
              size="xs"
              onClick={() => setEditing(true)}
            >
              Editar suíte
            </Button>
            <Button
              variant="subtle"
              size="xs"
              color="red"
              onClick={() => setRemove(open.id)}
            >
              Excluir suíte
            </Button>
          </Group>
        </Group>
        <Group gap="xs" wrap="wrap">
          <SuiteTemplateActions suite={open} onImported={refresh} />
          <SuiteDocActions suite={open} />
        </Group>
        <Paper p="lg" className="surface">
          <CasesPanel
            key={open.id}
            planId={planId}
            suite={open}
            description={description}
          />
        </Paper>
        {editing && (
          <SuiteForm initial={open} onClose={() => setEditing(false)} />
        )}
        {confirm}
      </Stack>
    );
  return (
    <Stack gap="lg">
      <Group justify="space-between" wrap="wrap" gap="md">
        <Stack gap={2}>
          <Text fw={800} size="lg">
            Suítes de teste
          </Text>
          <Text size="sm" c="dimmed">
            {suites.length} suíte(s) neste plano · clique em uma suíte para
            abrir os casos
          </Text>
        </Stack>
        <Tooltip label="Cria a próxima suíte deste plano">
          <Button loading={add.isPending} onClick={() => add.mutate()}>
            Nova suíte
          </Button>
        </Tooltip>
      </Group>
      <ResourceState
        loading={q.isLoading}
        error={q.error}
        empty={!suites.length}
        retry={() => q.refetch()}
      />
      <Stack gap="md">
        {paged.slice.map((s) => (
          <SuiteCard
            key={s.id}
            planId={planId}
            suite={s}
            onOpen={() => setOpenId(s.id)}
            onDelete={() => setRemove(s.id)}
          />
        ))}
      </Stack>
      <Pager
        page={paged.page}
        pages={paged.pages}
        size={paged.size}
        total={paged.total}
        from={paged.from}
        to={paged.to}
        onPage={paged.setPage}
        onSize={paged.setSize}
        unit="suítes"
      />
      {confirm}
    </Stack>
  );
}

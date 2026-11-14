import { useState, type ReactNode } from "react";
import {
  Alert,
  Anchor,
  Checkbox,
  ScrollArea,
  Table,
  Badge,
  Button,
  Divider,
  Group,
  Paper,
  Stack,
  Text,
  SimpleGrid,
  Select,
  MultiSelect,
  TextInput,
  Switch,
  ThemeIcon,
} from "@mantine/core";
import { useQuery, useMutation } from "@tanstack/react-query";
import { notifications } from "@mantine/notifications";
import { testSuiteApi } from "../../services/api/testSuiteApi";
import { statusLabel, statusOptions } from "../../utils/status";
import { demandApi } from "../../services/api/demandApi";
import { jiraApi } from "../../services/api/jiraApi";
import { catalogApi } from "../../services/api/catalogApi";
import { testPlanApi } from "../../services/api/testPlanApi";
import { useResource, useRefresh } from "../../hooks/useResource";
import type { Demand, DemandLocal } from "../../types/demand.types";
import type { CaseStatus } from "../../types/testCase.types";
import { AiSummaryCard } from "../ai/AiSummaryCard";
const ENDERECO = /(https?:\/\/[^\s<>"]+)/g;
const PONTUACAO_FINAL = /[.,;:!?)\]}]+$/;

/**
 * Mostra endereços da prévia como link, do mesmo jeito que eles chegam ao Jira.
 * A prévia só vale como conferência se for fiel ao que será publicado — e lá a
 * evidência do Vimeo vira link clicável.
 */
function comLinks(texto: string): ReactNode {
  if (!ENDERECO.test(texto)) return texto;
  ENDERECO.lastIndex = 0;
  return texto.split(ENDERECO).map((pedaco, i) => {
    if (i % 2 === 0) return pedaco;
    const endereco = pedaco.replace(PONTUACAO_FINAL, "");
    const sobra = pedaco.slice(endereco.length);
    return (
      <span key={i}>
        <Anchor href={endereco} target="_blank" rel="noopener noreferrer" size="sm">{endereco}</Anchor>
        {sobra}
      </span>
    );
  });
}

function Field({ label, value }: { label: string; value?: string | null }) {
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
      <Text size="sm" fw={550}>
        {value || "—"}
      </Text>
    </Stack>
  );
}
export function DemandOverview({
  demand: d,
  actions,
}: {
  demand: Demand;
  actions?: ReactNode;
}) {
  const refresh = useRefresh();
  const [form, setForm] = useState<DemandLocal>({
    testerId: d.testerId,
    testerOverride: d.testerOverride,
    moduleId: d.moduleId,
    technologyId: d.technologyId,
    callNumber: d.callNumber,
    testPlanId: d.testPlanId,
  });
  const [transition, setTransition] = useState<string | null>(null);
  const [sendSuite, setSendSuite] = useState(false);
  const [suiteId, setSuiteId] = useState<string | null>(null);
  // Quais status de caso entram no comentário. Vazio = a suíte inteira.
  const [caseStatuses, setCaseStatuses] = useState<CaseStatus[]>([]);
  const suites = useQuery({queryKey: ["suites", d.testPlanId], queryFn: () => testSuiteApi.byPlan(d.testPlanId!), enabled: sendSuite && !!d.testPlanId});
  const preview = useQuery({queryKey: ["suite-comment", d.jiraKey, d.testPlanId, suiteId, caseStatuses.join(",")], queryFn: () => demandApi.suiteComment(d.jiraKey, Number(suiteId), caseStatuses), enabled: sendSuite && !!suiteId, retry: false});
  const selectedSuite = suites.data?.find(s => String(s.id) === suiteId);
  const canMove = !!transition && (!sendSuite || (!!selectedSuite && !!preview.data && !preview.isFetching && !preview.isError));
  const testers = useResource(["testers"], () => catalogApi.list("testers"));
  const modules = useResource(["modules"], () => catalogApi.list("modules"));
  const tech = useResource(["technologies"], () =>
    catalogApi.list("technologies"),
  );
  const plans = useResource(["plans"], testPlanApi.list);
  const transitions = useResource(["transitions", d.jiraKey, d.statusId], () =>
    jiraApi.transitions(d.jiraKey),
  );
  const save = useMutation({
    mutationFn: () => demandApi.save(d.jiraKey, form),
    onSuccess: refresh,
  });
  const move = useMutation({
    mutationFn: () => {
      if (!canMove) throw new Error("Selecione a transição e a suíte para enviar.");
      return demandApi.transition(d.jiraKey, transition!, sendSuite ? Number(suiteId) : undefined, caseStatuses);
    },
    onSuccess: () => {
      notifications.show({color: "green", title: "Jira atualizado", message: sendSuite ? "Status alterado e conteúdo da suíte enviado em Atividade → Comentários." : "Status alterado no Jira."});
      setSendSuite(false);
      setSuiteId(null);
      setCaseStatuses([]);
      setTransition(null);
      refresh();
    },
  });
  const options = (list?: { id: number; name: string }[]) =>
    (list || []).map((t) => ({ value: String(t.id), label: t.name }));
  const planOptions = (plans.data || [])
    .filter((p) => p.jira.toUpperCase() === d.jiraKey.toUpperCase())
    .map((p) => ({ value: String(p.id), label: p.name + " #" + p.id }));
  return (
    <Stack gap="lg">
      {actions}
      <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="lg">
        <Paper p="xl" className="surface">
          <Stack gap="lg">
            <Group justify="space-between" align="flex-start">
              <Group gap="sm">
                <ThemeIcon size={34} radius="md" variant="light">
                  <span className="glyph">◈</span>
                </ThemeIcon>
                <Text fw={750} size="lg">
                  Descrição
                </Text>
              </Group>
              <Badge size="lg" variant="light">
                {d.statusName}
              </Badge>
            </Group>
            <Text className="description description-box" size="sm">
              {d.description || "Sem descrição no Jira."}
            </Text>
            <Divider />
            <SimpleGrid cols={2} spacing="lg">
              <Field label="Developer" value={d.assigneeName} />
              <Field label="Tester" value={d.testerName} />
              <Field label="Squad" value={d.squad} />
              <Field label="Sprint" value={d.sprint} />
            </SimpleGrid>
            <Text size="xs" c="dimmed">
              Developer, squad e sprint são controlados pelo Jira.
            </Text>
            <Divider />
            <Select
              label="Transição disponível no Jira"
              placeholder="Selecione uma transição"
              data={(transitions.data || []).map((t) => ({
                value: t.id,
                label: t.name + " → " + t.to.name,
              }))}
              value={transition}
              onChange={setTransition}
              disabled={move.isPending}
            />
            <Checkbox
              label="Enviar conteúdo do Excel da suíte nos comentários do Jira"
              checked={sendSuite}
              disabled={move.isPending || !d.testPlanId}
              onChange={e => {setSendSuite(e.currentTarget.checked); setSuiteId(null); setCaseStatuses([]);}}
            />
            {!d.testPlanId && <Text size="xs" c="dimmed">Vincule um plano de teste para selecionar sua suíte.</Text>}
            {sendSuite && <Stack gap="sm">
              <Select label="Suíte de teste para enviar" placeholder={suites.isLoading ? "Carregando suítes…" : "Selecione uma suíte"}
                searchable clearable value={suiteId} onChange={setSuiteId} disabled={move.isPending || suites.isLoading}
                data={(suites.data || []).map(s => ({value: String(s.id), label: "Suíte " + s.codeSuite + " · " + statusLabel(s.status) + " · " + (s.data || "Sem data")}))} />
              <MultiSelect
                label="Enviar apenas os casos nestes status"
                placeholder={caseStatuses.length ? undefined : "Todos os casos da suíte"}
                data={statusOptions}
                value={caseStatuses}
                onChange={values => setCaseStatuses(values as CaseStatus[])}
                clearable
                disabled={move.isPending}
                description="Deixe em branco para enviar a suíte inteira. Para enviar apenas os cenários com falha, marque Falhou; para os aprovados, Aprovado."
              />
              {suites.isError && <Alert color="red">Não foi possível carregar as suítes. <Button size="xs" variant="subtle" onClick={() => suites.refetch()}>Tentar novamente</Button></Alert>}
              {suites.isSuccess && !suites.data.length && <Text size="sm">Este plano ainda não tem suítes. Crie uma na aba Suítes de teste.</Text>}
              {preview.isFetching && <Text size="sm">Preparando conteúdo do Excel…</Text>}
              {preview.isError && <Alert color="red">{preview.error.message} <Button size="xs" variant="subtle" onClick={() => preview.refetch()}>Tentar novamente</Button></Alert>}
              {preview.data && <>
                <Text size="sm" fw={600}>{preview.data.title}</Text>
                <Text size="xs" c="dimmed">
                  {preview.data.cases === 1 ? "1 caso será publicado" : preview.data.cases + " casos serão publicados"} em Atividade → Comentários, com os campos de usuário e senha de teste preenchidos no Excel.
                </Text>
                <ScrollArea.Autosize mah={320} type="auto"><Table withTableBorder withColumnBorders striped style={{minWidth: 650}}><Table.Tbody>
                  {preview.data.rows.map((row, i) => <Table.Tr key={i}>{row.map((cell, j) => <Table.Td key={j} style={{whiteSpace: "pre-wrap", overflowWrap: "anywhere", maxWidth: 260}}>{comLinks(cell)}</Table.Td>)}</Table.Tr>)}
                </Table.Tbody></Table></ScrollArea.Autosize>
              </>}
            </Stack>}
            <Button
              variant="light"
              disabled={!canMove}
              loading={move.isPending}
              onClick={() => move.mutate()}
            >
              {sendSuite ? "Alterar status e enviar suíte ao Jira" : "Executar transição no Jira"}
            </Button>
          </Stack>
        </Paper>
        <Stack gap="lg">
          <Paper p="xl" className="surface">
            <form
              onSubmit={(e) => {
                e.preventDefault();
                save.mutate();
              }}
            >
              <Stack gap="lg">
                <Group gap="sm">
                  <ThemeIcon
                    size={34}
                    radius="md"
                    variant="light"
                    color="grape"
                  >
                    <span className="glyph">⚙</span>
                  </ThemeIcon>
                  <Text fw={750} size="lg">
                    Gestão local
                  </Text>
                </Group>
                <Select
                  clearable
                  searchable
                  label="Tester"
                  placeholder="Sem tester"
                  data={options(testers.data)}
                  value={form.testerId ? String(form.testerId) : null}
                  onChange={(v) =>
                    setForm({
                      ...form,
                      testerId: v ? Number(v) : null,
                      testerOverride: true,
                    })
                  }
                />
                <Switch
                  label="Manter tester local ao sincronizar"
                  checked={form.testerOverride}
                  onChange={(e) =>
                    setForm({
                      ...form,
                      testerOverride: e.currentTarget.checked,
                    })
                  }
                />
                <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="md">
                  <Select
                    clearable
                    searchable
                    label="Módulo"
                    placeholder="Sem módulo"
                    data={options(modules.data)}
                    value={form.moduleId ? String(form.moduleId) : null}
                    onChange={(v) =>
                      setForm({ ...form, moduleId: v ? Number(v) : null })
                    }
                  />
                  <Select
                    clearable
                    searchable
                    label="Tecnologia"
                    placeholder="Sem tecnologia"
                    data={options(tech.data)}
                    value={form.technologyId ? String(form.technologyId) : null}
                    onChange={(v) =>
                      setForm({ ...form, technologyId: v ? Number(v) : null })
                    }
                  />
                </SimpleGrid>
                <TextInput
                  label="Número do chamado"
                  placeholder="Chamado"
                  value={form.callNumber || ""}
                  onChange={(e) =>
                    setForm({ ...form, callNumber: e.target.value })
                  }
                />
                {planOptions.length > 1 && (
                  <Select
                    label="Plano existente"
                    description="Há mais de um plano com esta Jira Key. Escolha qual usar."
                    data={planOptions}
                    value={form.testPlanId ? String(form.testPlanId) : null}
                    onChange={(v) =>
                      setForm({ ...form, testPlanId: v ? Number(v) : null })
                    }
                  />
                )}
                <Button type="submit" loading={save.isPending}>
                  Salvar dados locais
                </Button>
              </Stack>
            </form>
          </Paper>
          <AiSummaryCard
            demandKey={d.jiraKey}
            description={d.description || ""}
            saved={d.aiSummary}
          />
        </Stack>
      </SimpleGrid>
    </Stack>
  );
}

import { useState } from "react";
import { Badge, Button, Group, Table, Text, Stack } from "@mantine/core";
import { useMutation } from "@tanstack/react-query";
import { testCaseApi } from "../../services/api/testCaseApi";
import { exportApi } from "../../services/api/exportApi";
import { useResource, useRefresh } from "../../hooks/useResource";
import { usePaged } from "../../hooks/usePaged";
import type { TestSuite } from "../../types/testSuite.types";
import type { TestCase } from "../../types/testCase.types";
import { ResourceState } from "../../components/feedback/ResourceState";
import { ConfirmDelete } from "../../components/common/ConfirmDelete";
import { Pager } from "../../components/common/Pager";
import { StatusProgress } from "../../components/common/StatusProgress";
import { statusColor, statusLabel, tally } from "../../utils/status";
import { TestCaseForm } from "./TestCaseForm";
import { AiGenerateModal } from "../ai/AiGenerateModal";
import { today } from "../../utils/dates";
import { ScenarioRecorder } from "./ScenarioRecorder";
import { videoEvidence } from "../../utils/videoEvidence";
export function CasesPanel({
  planId,
  suite,
  description,
}: {
  planId: number;
  suite: TestSuite;
  description: string;
}) {
  const q = useResource(["cases", suite.id], () =>
    testCaseApi.bySuite(planId, suite.id),
  );
  const [edit, setEdit] = useState<Partial<TestCase> | null>(null),
    [remove, setRemove] = useState<number | null>(null),
    [ai, setAi] = useState(false);
  const [record, setRecord] = useState<TestCase | null>(null);
  const refresh = useRefresh();
  const del = useMutation({
    mutationFn: testCaseApi.remove,
    onSuccess: () => {
      setRemove(null);
      refresh();
    },
  });
  const exp = useMutation({
    mutationFn: (format: "excel" | "pdf") => exportApi.suite(suite.id, format),
  });
  const rows = q.data || [];
  const t = tally(rows);
  const paged = usePaged(rows);
  return (
    <Stack>
      <Group justify="space-between" wrap="wrap" gap="md">
        <Stack gap={2}>
          <Text fw={750}>Casos de teste</Text>
          <Text size="sm" c="dimmed">
            {t.total} CTs · {t.done} aprovados · {t.donePercent}%
          </Text>
        </Stack>
        <Group gap="xs">
          <Button
            variant="default"
            size="xs"
            loading={exp.isPending}
            onClick={() => exp.mutate("excel")}
          >
            Excel
          </Button>
          <Button
            variant="default"
            size="xs"
            loading={exp.isPending}
            onClick={() => exp.mutate("pdf")}
          >
            PDF
          </Button>
          <Button variant="light" size="xs" onClick={() => setAi(true)}>
            Gerar CT com IA
          </Button>
          <Button
            size="xs"
            onClick={() =>
              setEdit({
                testSuiteId: suite.id,
                codeCase: Math.max(0, ...rows.map((c) => c.codeCase || 0)) + 1,
                status: "EM_PROGRESSO",
                data: today(),
                scenario: "",
                expectedResult: "",
              })
            }
          >
            Novo CT
          </Button>
        </Group>
      </Group>
      <StatusProgress tally={t} />
      <ResourceState
        loading={q.isLoading}
        error={q.error}
        empty={!rows.length}
      />
      <Table.ScrollContainer minWidth={420} type="native">
        <Table highlightOnHover layout="auto">
          <Table.Thead>
            <Table.Tr>
              <Table.Th w={1}>CT</Table.Th>
              <Table.Th>Cenário</Table.Th>
              <Table.Th visibleFrom="md">Resultado esperado</Table.Th>
              <Table.Th visibleFrom="sm">Execução</Table.Th>
              <Table.Th w={1}>Ações</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {paged.slice.map((c) => (
              <Table.Tr key={c.id}>
                <Table.Td>
                  <Text fw={700} size="sm">
                    {c.codeCase || c.id}
                  </Text>
                </Table.Td>
                <Table.Td className="cell-main">
                  <Text size="sm" lineClamp={3}>
                    {c.scenario}
                  </Text>
                  <Text
                    size="xs"
                    c="dimmed"
                    lineClamp={2}
                    hiddenFrom="md"
                    mt={4}
                  >
                    Esperado: {c.expectedResult}
                  </Text>
                  <Badge
                    variant="light"
                    size="sm"
                    mt={6}
                    hiddenFrom="sm"
                    color={statusColor(c.status)}
                  >
                    {statusLabel(c.status)}
                  </Badge>
                </Table.Td>
                <Table.Td visibleFrom="md" className="cell-main">
                  <Text size="sm" lineClamp={3}>
                    {c.expectedResult}
                  </Text>
                </Table.Td>
                <Table.Td visibleFrom="sm">
                  <Badge variant="light" color={statusColor(c.status)}>
                    {statusLabel(c.status)}
                  </Badge>
                </Table.Td>
                <Table.Td className="cell-action">
                  <Group gap="xs" wrap="wrap">
                    <Button size="xs" color="red" variant="light" onClick={() => setRecord(c)}>● REC</Button>
                    {videoEvidence(c.videoEvidence || "") && <Button size="xs" component="a" href={c.videoEvidence} target="_blank" rel="noopener noreferrer" variant="subtle">Ver vídeo</Button>}
                    <Button
                      size="xs"
                      variant="subtle"
                      onClick={() => setEdit(c)}
                    >
                      Editar / executar
                    </Button>
                    <Button
                      size="xs"
                      variant="subtle"
                      color="red"
                      onClick={() => setRemove(c.id)}
                    >
                      Excluir
                    </Button>
                  </Group>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Table.ScrollContainer>
      <Pager
        page={paged.page}
        pages={paged.pages}
        size={paged.size}
        total={paged.total}
        from={paged.from}
        to={paged.to}
        onPage={paged.setPage}
        onSize={paged.setSize}
        unit="casos"
      />
      {record && <ScenarioRecorder caseId={record.id} scenario={record.scenario} previousUrl={record.videoEvidence} onSaved={() => refresh()} onClose={() => setRecord(null)} />}
      {edit && <TestCaseForm initial={edit} onClose={() => setEdit(null)} />}
      <ConfirmDelete
        opened={remove !== null}
        onClose={() => setRemove(null)}
        loading={del.isPending}
        onConfirm={() => remove !== null && del.mutate(remove)}
      />
      {ai && (
        <AiGenerateModal
          suiteId={suite.id}
          description={description}
          onClose={() => setAi(false)}
        />
      )}
    </Stack>
  );
}

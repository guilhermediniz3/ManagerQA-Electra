import { useState } from "react";
import { iaPronta } from "../integrations/local";
import { Link } from "react-router-dom";
import {
  Alert,
  Anchor,
  Badge,
  Button,
  Divider,
  Group,
  List,
  Paper,
  Select,
  Stack,
  Text,
  ThemeIcon,
  Tooltip,
} from "@mantine/core";
import { useMutation } from "@tanstack/react-query";
import { aiApi } from "../../services/api/aiApi";
import { demandApi } from "../../services/api/demandApi";
import { integrationApi } from "../../services/api/integrationApi";
import { useResource, useRefresh } from "../../hooks/useResource";
import type { AiSummary, StoredAiSummary } from "../../types/ai.types";
const NAMES: Record<string, string> = {
  openai: "OpenAI",
  claude: "Claude",
  gemini: "Gemini",
  groq: "Groq",
};
const STATUS: Record<string, { label: string; color: string }> = {
  CONNECTED: { label: "conectado", color: "teal" },
  UNTESTED: { label: "não testado", color: "yellow" },
  ERROR: { label: "com erro", color: "red" },
};
const same = (a: AiSummary | null, b: AiSummary | null) =>
  JSON.stringify(
    a && { s: a.summary, p: a.points, q: a.questions, v: a.provider },
  ) ===
  JSON.stringify(
    b && { s: b.summary, p: b.points, q: b.questions, v: b.provider },
  );
export function AiSummaryCard({
  demandKey,
  description,
  saved,
}: {
  demandKey: string;
  description: string;
  saved: StoredAiSummary | null;
}) {
  const integrations = useResource(["integrations"], integrationApi.list);
  const refresh = useRefresh();
  const [provider, setProvider] = useState<string | null>(null);
  const [summary, setSummary] = useState<AiSummary | null>(saved);
  const configured = (integrations.data || []).filter(iaPronta);
  const chosen =
    provider ||
    configured.find((i) => i.defaultProvider)?.provider ||
    configured[0]?.provider ||
    null;
  const run = useMutation({
    mutationFn: () => aiApi.summary(chosen || "", description),
    onSuccess: setSummary,
  });
  const persist = useMutation({
    mutationFn: () => demandApi.saveAiSummary(demandKey, summary!),
    onSuccess: refresh,
  });
  const unsaved = !!summary && !same(summary, saved);
  const empty = !description.trim();
  return (
    <Paper p="lg" className="surface ai-card">
      <Stack gap="md">
        <Group justify="space-between" wrap="wrap" gap="xs">
          <Group gap="sm" wrap="nowrap">
            <ThemeIcon size={32} radius="md" variant="light" color="grape">
              <span className="glyph">✦</span>
            </ThemeIcon>
            <Stack gap={0}>
              <Text fw={750}>Resumo com IA</Text>
              <Text size="xs" c="dimmed">
                Entenda a demanda antes de testar
              </Text>
            </Stack>
          </Group>
          <Group gap="xs">
            {unsaved && (
              <Badge variant="light" color="yellow">
                Não salvo
              </Badge>
            )}
            {summary?.provider && (
              <Badge variant="light" color="grape">
                {NAMES[summary.provider] || summary.provider}
              </Badge>
            )}
          </Group>
        </Group>
        {!configured.length ? (
          <Alert variant="light" color="gray">
            Nenhuma IA configurada. Cadastre uma credencial em{" "}
            <Anchor component={Link} to="/integrations">
              Integrações
            </Anchor>
            .
          </Alert>
        ) : (
          <>
            <Select
              label="IA"
              data={configured.map((i) => ({
                value: i.provider,
                label:
                  (NAMES[i.provider] || i.provider) +
                  " · " +
                  (i.model || "sem modelo") +
                  " · " +
                  (STATUS[i.status]?.label || "não configurado"),
              }))}
              value={chosen}
              onChange={setProvider}
              allowDeselect={false}
            />
            <Tooltip
              label="A descrição da demanda será enviada para a IA selecionada"
              disabled={empty}
            >
              <Button
                variant="light"
                color="grape"
                loading={run.isPending}
                disabled={empty || !chosen}
                onClick={() => run.mutate()}
              >
                {summary ? "Gerar novamente" : "Resumir demanda"}
              </Button>
            </Tooltip>
            {empty && (
              <Text size="xs" c="dimmed">
                Esta demanda não tem descrição no Jira para resumir.
              </Text>
            )}
            {run.error && (
              <Alert color="red" variant="light">
                {(run.error as Error).message}
              </Alert>
            )}
            {summary && (
              <>
                <Divider />
                <Text size="sm" className="description ai-summary-text">
                  {summary.summary}
                </Text>
                {!!summary.points.length && (
                  <Stack gap={6}>
                    <Text size="xs" fw={800} c="dimmed" className="field-label">
                      PONTOS DE ATENÇÃO
                    </Text>
                    <List size="sm" spacing={4} className="ai-list">
                      {summary.points.map((point) => (
                        <List.Item key={point}>{point}</List.Item>
                      ))}
                    </List>
                  </Stack>
                )}
                {!!summary.questions.length && (
                  <Stack gap={6}>
                    <Text size="xs" fw={800} c="dimmed" className="field-label">
                      DÚVIDAS PARA O TIME
                    </Text>
                    <List size="sm" spacing={4} className="ai-list">
                      {summary.questions.map((question) => (
                        <List.Item key={question}>{question}</List.Item>
                      ))}
                    </List>
                  </Stack>
                )}
                <Group justify="space-between" wrap="wrap" gap="sm">
                  <Text size="xs" c="dimmed">
                    {saved?.savedAt
                      ? "Salvo em " +
                        new Date(saved.savedAt).toLocaleString("pt-BR")
                      : "Ainda não salvo nesta demanda."}
                  </Text>
                  <Button
                    size="xs"
                    color="grape"
                    loading={persist.isPending}
                    disabled={!unsaved}
                    onClick={() => persist.mutate()}
                  >
                    {unsaved ? "Salvar resumo" : "Resumo salvo"}
                  </Button>
                </Group>
                <Text size="xs" c="dimmed">
                  Gerado por IA a partir da descrição do Jira. Confira antes de
                  usar como requisito.
                </Text>
              </>
            )}
          </>
        )}
      </Stack>
    </Paper>
  );
}

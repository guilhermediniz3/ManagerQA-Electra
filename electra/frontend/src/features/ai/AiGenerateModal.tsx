import { useState } from "react";
import { iaPronta } from "../integrations/local";
import {
  Modal,
  Stack,
  Select,
  Textarea,
  Button,
  Paper,
  Checkbox,
  Text,
  Group,
  Alert,
} from "@mantine/core";
import { useMutation } from "@tanstack/react-query";
import { aiApi } from "../../services/api/aiApi";
import { integrationApi } from "../../services/api/integrationApi";
import { useResource, useRefresh } from "../../hooks/useResource";
import type { CaseDraft } from "../../types/testCase.types";
export function AiGenerateModal({
  suiteId,
  description,
  onClose,
}: {
  suiteId: number;
  description: string;
  onClose: () => void;
}) {
  const [provider, setProvider] = useState<string | null>(null),
    [text, setText] = useState(description),
    [drafts, setDrafts] = useState<(CaseDraft & { selected: boolean })[]>([]);
  const integrations = useResource(["integrations"], integrationApi.list);
  const refresh = useRefresh();
  const selectedProvider =
    provider ||
    integrations.data?.find((i) => i.defaultProvider)?.provider ||
    null;
  const gen = useMutation({
    mutationFn: () => aiApi.preview(selectedProvider || "", text),
    onSuccess: (items) =>
      setDrafts(items.map((i) => ({ ...i, selected: true }))),
  });
  const imp = useMutation({
    mutationFn: () =>
      aiApi.import(
        suiteId,
        drafts
          .filter((d) => d.selected)
          .map(({ scenario, expectedResult }) => ({
            scenario,
            expectedResult,
          })),
      ),
    onSuccess: () => {
      refresh();
      onClose();
    },
  });
  return (
    <Modal opened onClose={onClose} title="Gerar casos com IA" size="xl">
      <Stack>
        <Alert color="blue">
          A descrição será enviada ao provider selecionado. Revise e edite os
          casos antes de importar.
        </Alert>
        <Select
          label="Provider"
          data={(integrations.data || [])
            .filter(iaPronta)
            .map((i) => ({
              value: i.provider,
              label: i.provider + " · " + (i.model || "modelo não configurado"),
            }))}
          value={selectedProvider}
          onChange={setProvider}
        />
        <Textarea
          label="Descrição / requisitos"
          autosize
          minRows={5}
          maxRows={10}
          value={text}
          onChange={(e) => setText(e.target.value)}
        />
        <Button
          loading={gen.isPending}
          disabled={!selectedProvider || !text.trim()}
          onClick={() => gen.mutate()}
        >
          Gerar preview
        </Button>
        {drafts.map((d, index) => (
          <Paper key={index} p="md">
            <Stack gap="sm">
              <Checkbox
                label={"Importar CT " + (index + 1)}
                checked={d.selected}
                onChange={(e) =>
                  setDrafts(
                    drafts.map((v, i) =>
                      i === index
                        ? { ...v, selected: e.currentTarget.checked }
                        : v,
                    ),
                  )
                }
              />
              <Textarea
                label="Cenário"
                maxLength={255}
                value={d.scenario}
                onChange={(e) =>
                  setDrafts(
                    drafts.map((v, i) =>
                      i === index ? { ...v, scenario: e.target.value } : v,
                    ),
                  )
                }
              />
              <Textarea
                label="Resultado esperado"
                value={d.expectedResult}
                onChange={(e) =>
                  setDrafts(
                    drafts.map((v, i) =>
                      i === index
                        ? { ...v, expectedResult: e.target.value }
                        : v,
                    ),
                  )
                }
              />
            </Stack>
          </Paper>
        ))}
        {!!drafts.length && (
          <Group justify="space-between">
            <Text size="sm">
              {drafts.filter((d) => d.selected).length} selecionados
            </Text>
            <Button
              loading={imp.isPending}
              disabled={!drafts.some((d) => d.selected)}
              onClick={() => imp.mutate()}
            >
              Importar selecionados
            </Button>
          </Group>
        )}
      </Stack>
    </Modal>
  );
}

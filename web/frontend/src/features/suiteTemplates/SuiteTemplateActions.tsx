import { useState } from "react";
import {
  Button,
  Group,
  Modal,
  Select,
  Stack,
  Text,
  TextInput,
  Textarea,
} from "@mantine/core";
import { useMutation, useQuery } from "@tanstack/react-query";
import { notifications } from "@mantine/notifications";
import { suiteTemplateApi } from "../../services/api/suiteTemplateApi";
import type { TestSuite } from "../../types/testSuite.types";

/**
 * Os dois caminhos entre uma suíte e o catálogo de templates: trazer um roteiro
 * pronto para dentro da suíte, e transformar a suíte atual em roteiro.
 *
 * Fica aqui, e não dentro do painel de suítes, porque é um assunto só —
 * reaproveitamento — e assim a tela de suítes não cresce por causa disso.
 */
export function SuiteTemplateActions({
  suite,
  onImported,
}: {
  suite: TestSuite;
  onImported: () => void;
}) {
  const [templateId, setTemplateId] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [nome, setNome] = useState("");
  const [descricao, setDescricao] = useState("");

  const templates = useQuery({
    queryKey: ["suite-templates-ativos"],
    queryFn: suiteTemplateApi.available,
  });

  const importar = useMutation({
    mutationFn: () => suiteTemplateApi.importInto(Number(templateId), suite.id),
    onSuccess: (resultado) => {
      notifications.show({
        color: "green",
        title: "Template importado",
        message:
          resultado.imported === 1
            ? "1 caso de teste foi acrescentado à suíte."
            : `${resultado.imported} casos de teste foram acrescentados à suíte.`,
      });
      setTemplateId(null);
      onImported();
    },
  });

  const enviar = useMutation({
    mutationFn: () =>
      suiteTemplateApi.fromSuite(suite.id, {
        name: nome,
        description: descricao,
      }),
    onSuccess: (template) => {
      notifications.show({
        color: "green",
        title: "Template criado",
        message: `"${template.name}" já pode ser importado em outras suítes.`,
      });
      setEnviando(false);
      setNome("");
      setDescricao("");
      void templates.refetch();
    },
  });

  const semTemplates = templates.isSuccess && templates.data.length === 0;

  return (
    <>
      <Group gap="xs" wrap="nowrap">
        <Select
          size="xs"
          w={240}
          searchable
          clearable
          placeholder={
            templates.isLoading
              ? "Carregando templates…"
              : semTemplates
                ? "Nenhum template criado"
                : "Importar de um template…"
          }
          disabled={templates.isLoading || semTemplates}
          value={templateId}
          onChange={setTemplateId}
          data={(templates.data ?? []).map((t) => ({
            value: String(t.id),
            label: `${t.name} · ${t.totalCases} caso${t.totalCases === 1 ? "" : "s"}`,
          }))}
        />
        <Button
          size="xs"
          variant="default"
          disabled={!templateId}
          loading={importar.isPending}
          onClick={() => importar.mutate()}
        >
          Importar
        </Button>
        <Button size="xs" variant="default" onClick={() => setEnviando(true)}>
          Enviar para template
        </Button>
      </Group>

      {importar.isError && (
        <Text size="xs" c="red">
          {(importar.error as Error).message}
        </Text>
      )}

      <Modal
        opened={enviando}
        onClose={() => setEnviando(false)}
        title={`Enviar a suíte ${suite.codeSuite} para template`}
      >
        <Stack gap="md">
          <Text size="sm" c="dimmed">
            Os casos desta suíte viram um roteiro reutilizável. Vai só cenário e
            resultado esperado — resultado obtido, evidência e status continuam
            sendo desta suíte.
          </Text>
          <TextInput
            label="Nome do template"
            placeholder="Em branco usa o nome do plano de teste"
            value={nome}
            onChange={(e) => setNome(e.currentTarget.value)}
          />
          <Textarea
            label="Descrição"
            placeholder="Quando usar este template"
            autosize
            minRows={2}
            value={descricao}
            onChange={(e) => setDescricao(e.currentTarget.value)}
          />
          {enviar.isError && (
            <Text size="sm" c="red">
              {(enviar.error as Error).message}
            </Text>
          )}
          <Group justify="flex-end" gap="sm">
            <Button variant="subtle" onClick={() => setEnviando(false)}>
              Cancelar
            </Button>
            <Button loading={enviar.isPending} onClick={() => enviar.mutate()}>
              Criar template
            </Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}

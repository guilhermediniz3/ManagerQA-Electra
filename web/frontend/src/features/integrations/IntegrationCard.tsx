import { useState } from "react";
import {
  Autocomplete,
  Badge,
  Button,
  Card,
  Group,
  Modal,
  PasswordInput,
  Stack,
  Text,
  TextInput,
  Switch,
  Select,
  Alert,
} from "@mantine/core";
import { useMutation } from "@tanstack/react-query";
import type {
  Integration,
  IntegrationInput,
} from "../../types/integration.types";
import { ehLocal } from "./local";
import { LocalAiPanel } from "./LocalAiPanel";
import { integrationApi } from "../../services/api/integrationApi";
import { jiraApi } from "../../services/api/jiraApi";
import { aiApi } from "../../services/api/aiApi";
import { useRefresh } from "../../hooks/useResource";
export function IntegrationCard({ value }: { value: Integration }) {
  const [opened, setOpened] = useState(false);
  const jira = value.provider === "jira";
  // Roda na própria máquina: não há chave de API, e o endereço é o localhost
  // do serviço — configurável só para quem aponta para outra porta ou máquina.
  const local = ehLocal(value.provider);
  const [form, setForm] = useState<IntegrationInput>({
    ...value,
    baseUrl: jira
      ? value.baseUrl || ""
      : value.baseUrl,
    username: jira
      ? value.username || ""
      : value.username,
    secret: "",
  });
  const refresh = useRefresh();
  const save = useMutation({
    mutationFn: () => integrationApi.save(value.provider, form),
    onSuccess: () => {
      setOpened(false);
      setForm({ ...form, secret: "" });
      refresh();
    },
  });
  const test = useMutation({
    mutationFn: () => integrationApi.test(value.provider),
    onSettled: () => refresh(),
  });
  const fields = useMutation({ mutationFn: () => jiraApi.fields() });
  const models = useMutation({
    mutationFn: () => aiApi.models(value.provider),
  });
  const fieldOptions = (fields.data || [])
    .filter(
      (f) =>
        f.id.startsWith("customfield_") ||
        ["project", "components"].includes(f.id),
    )
    .map((f) => ({ value: f.id, label: f.name + " · " + f.id }));
  const status: Record<string, string> = {
    CONNECTED: "Conectado",
    ERROR: "Erro",
    NOT_CONFIGURED: "Não configurado",
    UNTESTED: "Não testado",
  };
  return (
    <>
      <Card withBorder padding="lg">
        <Group justify="space-between">
          <Text fw={700} tt="uppercase">
            {value.provider}
          </Text>
          <Badge
            color={
              value.status === "CONNECTED"
                ? "teal"
                : value.status === "ERROR"
                  ? "red"
                  : "gray"
            }
          >
            {status[value.status] || value.status}
          </Badge>
        </Group>
        <Text c="dimmed" size="sm" mt="md">
          {value.provider === "jira"
            ? "Demandas, usuários e transições"
            : value.model || "Geração assistida de casos de teste"}
        </Text>
        <Text size="sm" mt="sm">
          {local
            ? value.baseUrl || "http://127.0.0.1:11434 (padrão)"
            : value.maskedSecret || "Nenhuma credencial salva"}
        </Text>
        <Group mt="lg">
          <Button variant="light" onClick={() => setOpened(true)}>
            Configurar
          </Button>
          <Button
            variant="default"
            loading={test.isPending}
            disabled={!local && !value.maskedSecret}
            onClick={() => test.mutate()}
          >
            Testar conexão
          </Button>
        </Group>
      </Card>
      <Modal
        opened={opened}
        onClose={() => setOpened(false)}
        title={"Configurar " + value.provider}
        size="lg"
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            save.mutate();
          }}
        >
          <Stack>
            {value.provider === "jira" ? (
              <>
                <TextInput
                  label="Site Jira"
                  value={form.baseUrl}
                  required
                  onChange={(e) =>
                    setForm({ ...form, baseUrl: e.target.value })
                  }
                />
                <TextInput
                  label="E-mail"
                  type="email"
                  required
                  value={form.username}
                  onChange={(e) =>
                    setForm({ ...form, username: e.target.value })
                  }
                />
              </>
            ) : (
              <>
                <Autocomplete
                  label="Modelo"
                  description={
                    local
                      ? "O modelo em uso. Escolha um dos baixados abaixo."
                      : "Escolha um modelo da sua conta ou digite o identificador."
                  }
                  placeholder="identificador do modelo"
                  data={models.data || (form.model ? [form.model] : [])}
                  limit={50}
                  value={form.model || ""}
                  onChange={(model) => setForm({ ...form, model })}
                />
                {local && (
                  <LocalAiPanel
                    modelo={form.model || ""}
                    onEscolher={(model) => setForm({ ...form, model })}
                  />
                )}
                {!local && (
                  <>
                    <Button
                      variant="light"
                      loading={models.isPending}
                      disabled={!value.maskedSecret}
                      onClick={() => models.mutate()}
                    >
                      {value.maskedSecret
                        ? "Descobrir modelos disponíveis"
                        : "Salve a credencial para descobrir os modelos"}
                    </Button>
                    {models.isSuccess && (
                      <Text size="xs" c="dimmed">
                        {models.data.length} modelo(s) disponível(is) nesta
                        conta.
                      </Text>
                    )}
                  </>
                )}
                {!local &&
                  models.isSuccess &&
                  !!form.model &&
                  !models.data.includes(form.model) && (
                    <Alert color="yellow" variant="light">
                      O modelo <b>{form.model}</b> não está na lista da sua
                      conta — a geração vai falhar com HTTP 404. Escolha um da
                      lista.
                    </Alert>
                  )}
              </>
            )}
            {local && (
              <TextInput
                label="Endereço do serviço"
                description="Em branco usa http://127.0.0.1:11434, que é onde o Ollama atende por padrão."
                placeholder="http://127.0.0.1:11434"
                value={form.baseUrl || ""}
                onChange={(e) => setForm({ ...form, baseUrl: e.target.value })}
              />
            )}
            {!local && (
            <PasswordInput
              label={value.provider === "jira" ? "API Token" : "API Key"}
              placeholder={
                value.maskedSecret
                  ? "Deixe vazio para manter a credencial"
                  : "Informe a credencial"
              }
              value={form.secret || ""}
              onChange={(e) => setForm({ ...form, secret: e.target.value })}
              autoComplete="new-password"
            />
            )}
            {value.provider !== "jira" ? (
              <Switch
                label="Usar como provider padrão"
                checked={form.defaultProvider}
                onChange={(e) =>
                  setForm({ ...form, defaultProvider: e.currentTarget.checked })
                }
              />
            ) : (
              <>
                <Alert color="blue">
                  Salve e teste a conexão antes de descobrir os campos. Squad
                  não é presumida como projeto; selecione o campo conforme a
                  estrutura real do Jira.
                </Alert>
                <Button
                  variant="light"
                  loading={fields.isPending}
                  onClick={() => fields.mutate()}
                >
                  Descobrir campos do Jira
                </Button>
                {(["squadField", "sprintField", "testerField"] as const).map(
                  (key, i) => (
                    <Select
                      key={key}
                      searchable
                      clearable
                      label={["Campo Squad", "Campo Sprint", "Campo Tester"][i]}
                      value={form[key]}
                      data={
                        fieldOptions.some((f) => f.value === form[key]) ||
                        !form[key]
                          ? fieldOptions
                          : [
                              ...fieldOptions,
                              { value: form[key]!, label: form[key]! },
                            ]
                      }
                      onChange={(v) => setForm({ ...form, [key]: v })}
                    />
                  ),
                )}
              </>
            )}
            <Button type="submit" loading={save.isPending}>
              Salvar configuração
            </Button>
          </Stack>
        </form>
      </Modal>
    </>
  );
}

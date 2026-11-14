import { useState } from "react";
import {
  Alert,
  Anchor,
  Badge,
  Button,
  Code,
  Group,
  Modal,
  Paper,
  ScrollArea,
  Stack,
  Switch,
  Text,
  TextInput,
  Textarea,
  Title,
} from "@mantine/core";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { notifications } from "@mantine/notifications";
import { runManagerApi } from "../../services/api/runManagerApi";
import type { RunFailure } from "../../types/runmanager.types";
import { abrirArquivo, podeAbrirArquivo } from "./SeletorDePasta";

const BUCKETS: Record<string, { rotulo: string; cor: string }> = {
  REGRA_NEGOCIO: { rotulo: "Regra de negócio", cor: "red" },
  STATUS_CODE: { rotulo: "Resposta do backend", cor: "orange" },
  LOCATOR: { rotulo: "Elemento / locator", cor: "yellow" },
  INFRA: { rotulo: "Infraestrutura", cor: "gray" },
  OUTRO: { rotulo: "Outro", cor: "blue" },
};

/**
 * Falhas das execuções, e o que fazer com elas.
 *
 * Cada falha vem do relatório do Playwright já classificada, porque falha de
 * produto e falha de automação levam a ações opostas. Abrir demanda e enviar o
 * vídeo são botões, e não automatismo: subir todo vídeo encheria a conta do
 * Vimeo, e abrir demanda de tudo encheria o Jira de tarefa por triar.
 */
export function RunFailuresTab() {
  const queryClient = useQueryClient();
  const [aberta, setAberta] = useState<RunFailure | null>(null);
  const [demanda, setDemanda] = useState<{
    falha: RunFailure;
    projectKey: string;
    issueType: string;
    summary: string;
    description: string;
    enviarVideo: boolean;
  } | null>(null);

  const falhas = useQuery({
    queryKey: ["runmanager", "failures"],
    queryFn: () => runManagerApi.failures(),
  });

  const vimeo = useQuery({
    queryKey: ["runmanager", "vimeo-status"],
    queryFn: runManagerApi.vimeoStatus,
  });

  const atualizar = () =>
    void queryClient.invalidateQueries({ queryKey: ["runmanager", "failures"] });

  const enviarVideo = useMutation({
    mutationFn: (id: number) => runManagerApi.sendVideo(id),
    onSuccess: (resposta) => {
      notifications.show({ color: "green", message: "Vídeo no Vimeo: " + resposta.url });
      atualizar();
    },
    onError: (erro: Error) =>
      notifications.show({ color: "red", title: "Não foi possível enviar", message: erro.message }),
  });

  const criarDemanda = useMutation({
    mutationFn: () =>
      runManagerApi.createIssue(demanda!.falha.id, {
        projectKey: demanda!.projectKey,
        issueType: demanda!.issueType,
        summary: demanda!.summary,
        description: demanda!.description,
        enviarVideo: demanda!.enviarVideo,
      }),
    onSuccess: (resposta) => {
      notifications.show({ color: "green", title: "Demanda criada", message: resposta.key });
      setDemanda(null);
      atualizar();
    },
    onError: (erro: Error) =>
      notifications.show({ color: "red", title: "Não foi possível criar", message: erro.message }),
  });

  const lista = falhas.data ?? [];

  return (
    <Stack gap="lg">
      <div>
        <Title order={4}>Falhas e execuções</Title>
        <Text size="xs" c="dimmed">
          Lidas do relatório do Playwright ao fim de cada execução.
        </Text>
      </div>

      {lista.length === 0 ? (
        <Alert variant="light" color="teal">
          Nenhuma falha registrada. Elas aparecem aqui sozinhas quando uma execução
          terminar com casos falhando.
        </Alert>
      ) : (
        <Stack gap="xs">
          {lista.map((f) => (
            <Paper key={f.id} p="md" withBorder radius="md">
              <Group justify="space-between" align="flex-start" wrap="nowrap">
                <div style={{ flex: 1, minWidth: 0 }}>
                  <Group gap="xs" mb={4}>
                    <Badge size="sm" variant="light" color={BUCKETS[f.bucket]?.cor ?? "blue"}>
                      {BUCKETS[f.bucket]?.rotulo ?? f.bucket}
                    </Badge>
                    {f.statusCode && (
                      <Badge size="sm" variant="outline">
                        HTTP {f.statusCode}
                      </Badge>
                    )}
                    {f.version && (
                      <Badge size="sm" variant="outline">
                        {f.version}
                      </Badge>
                    )}
                    {f.module && (
                      <Badge size="sm" variant="light">
                        {f.module}
                      </Badge>
                    )}
                    {f.videoPath && (
                      <Badge size="sm" color="grape" variant="light">
                        vídeo
                      </Badge>
                    )}
                    {f.jiraKey && (
                      <Badge size="sm" color="indigo" variant="filled">
                        {f.jiraKey}
                      </Badge>
                    )}
                  </Group>
                  <Text size="sm" fw={600}>
                    {f.title}
                  </Text>
                  <Text size="xs" c="dimmed" lineClamp={2}>
                    {f.summary}
                  </Text>
                  <Text size="10px" c="dimmed" mt={2}>
                    {f.file} · execução #{f.executionId} — {f.executionTitle}
                  </Text>
                  {f.vimeoUrl && (
                    <Anchor href={f.vimeoUrl} target="_blank" size="xs">
                      Ver vídeo no Vimeo
                    </Anchor>
                  )}
                </div>
                <Stack gap={4}>
                  <Button size="compact-xs" variant="default" onClick={() => setAberta(f)}>
                    Detalhes
                  </Button>
                  {/*
                    Assistir não depende do Vimeo: o arquivo está no disco desde
                    que o teste rodou, e mandá-lo para a nuvem só para poder vê-lo
                    seria caminho longo demais.
                  */}
                  {f.videoPath && podeAbrirArquivo() && (
                    <Button
                      size="compact-xs"
                      variant="light"
                      onClick={() => void abrirArquivo(f.videoPath!)}
                    >
                      Assistir vídeo
                    </Button>
                  )}
                  {f.videoPath && !f.vimeoUrl && vimeo.data?.configured && (
                    <Button
                      size="compact-xs"
                      variant="subtle"
                      loading={enviarVideo.isPending && enviarVideo.variables === f.id}
                      onClick={() => enviarVideo.mutate(f.id)}
                    >
                      Enviar vídeo
                    </Button>
                  )}
                  {!f.jiraKey && (
                    <Button
                      size="compact-xs"
                      variant="light"
                      onClick={() =>
                        setDemanda({
                          falha: f,
                          projectKey: "",
                          issueType: "Bug",
                          summary: (f.module ? "[" + f.module + "] " : "") + f.title,
                          description: "",
                          enviarVideo: !!f.videoPath && !!vimeo.data?.configured,
                        })
                      }
                    >
                      Criar demanda
                    </Button>
                  )}
                </Stack>
              </Group>
            </Paper>
          ))}
        </Stack>
      )}

      <Modal
        opened={!!aberta}
        onClose={() => setAberta(null)}
        title={aberta?.title}
        size="xl"
      >
        {aberta && (
          <Stack gap="sm">
            <Text size="sm" c="dimmed">
              {aberta.fullName}
            </Text>
            <ScrollArea.Autosize mah={420}>
              <Code block style={{ whiteSpace: "pre-wrap", fontSize: 12 }}>
                {aberta.details}
              </Code>
            </ScrollArea.Autosize>
            {aberta.videoPath && (
              <Group gap="xs" align="center">
                <Text size="xs" c="dimmed" style={{ flex: 1 }}>
                  Vídeo: {aberta.videoPath}
                </Text>
                {podeAbrirArquivo() && (
                  <Button
                    size="compact-xs"
                    variant="light"
                    onClick={() => void abrirArquivo(aberta.videoPath!)}
                  >
                    Abrir
                  </Button>
                )}
              </Group>
            )}
            {aberta.screenshotPath && (
              <Group gap="xs" align="center">
                <Text size="xs" c="dimmed" style={{ flex: 1 }}>
                  Screenshot: {aberta.screenshotPath}
                </Text>
                {podeAbrirArquivo() && (
                  <Button
                    size="compact-xs"
                    variant="light"
                    onClick={() => void abrirArquivo(aberta.screenshotPath!)}
                  >
                    Abrir
                  </Button>
                )}
              </Group>
            )}
          </Stack>
        )}
      </Modal>

      <Modal
        opened={!!demanda}
        onClose={() => setDemanda(null)}
        title="Criar demanda no Jira"
        size="lg"
      >
        {demanda && (
          <Stack gap="md">
            <Group grow>
              <TextInput
                label="Projeto"
                description="A chave do projeto, como SOP."
                required
                value={demanda.projectKey}
                onChange={(e) =>
                  setDemanda({ ...demanda, projectKey: e.currentTarget.value.toUpperCase() })
                }
              />
              <TextInput
                label="Tipo"
                value={demanda.issueType}
                onChange={(e) => setDemanda({ ...demanda, issueType: e.currentTarget.value })}
              />
            </Group>
            <TextInput
              label="Resumo"
              value={demanda.summary}
              onChange={(e) => setDemanda({ ...demanda, summary: e.currentTarget.value })}
            />
            <Textarea
              label="Observação"
              description="Entra antes dos dados da falha, que vão junto automaticamente."
              autosize
              minRows={3}
              value={demanda.description}
              onChange={(e) => setDemanda({ ...demanda, description: e.currentTarget.value })}
            />
            {demanda.falha.videoPath && (
              <Switch
                label="Enviar o vídeo ao Vimeo e incluir o link"
                disabled={!vimeo.data?.configured}
                description={
                  vimeo.data?.configured
                    ? "O envio acontece antes de criar a demanda e pode levar alguns minutos."
                    : "Configure o token do Vimeo em Integrações para habilitar."
                }
                checked={demanda.enviarVideo}
                onChange={(e) =>
                  setDemanda({ ...demanda, enviarVideo: e.currentTarget.checked })
                }
              />
            )}
            <Alert variant="light" color="blue">
              O log da falha vai no corpo da demanda, em bloco de código.
            </Alert>
            <Group justify="flex-end">
              <Button variant="subtle" onClick={() => setDemanda(null)}>
                Cancelar
              </Button>
              <Button
                loading={criarDemanda.isPending}
                disabled={!demanda.projectKey.trim()}
                onClick={() => criarDemanda.mutate()}
              >
                Criar demanda
              </Button>
            </Group>
          </Stack>
        )}
      </Modal>
    </Stack>
  );
}

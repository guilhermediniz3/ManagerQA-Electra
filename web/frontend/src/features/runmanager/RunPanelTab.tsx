import { useState } from "react";
import {
  Alert,
  Badge,
  Button,
  Group,
  Modal,
  Paper,
  Select,
  SimpleGrid,
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
import type {
  RunCard,
  RunContainer,
  RunExecution,
} from "../../types/runmanager.types";
import { RunTerminal } from "./RunTerminal";

const cardVazio = (containerId?: number | null): RunCard => ({
  containerId,
  title: "",
  script: "",
  active: true,
  position: 0,
});

/**
 * Painel de disparo.
 *
 * Os blocos e os botões vêm do cadastro, não de uma conta feita no código. É a
 * diferença que o documento pede: antes, acrescentar um atalho exigia publicar
 * uma versão; aqui é um formulário.
 *
 * Produção pede confirmação antes de rodar — o botão vermelho avisa, mas quem
 * está com pressa clica no vermelho do mesmo jeito.
 */
export function RunPanelTab({ modoEdicao }: { modoEdicao: boolean }) {
  const queryClient = useQueryClient();
  const [container, setContainer] = useState<Partial<RunContainer> | null>(null);
  const [card, setCard] = useState<Partial<RunCard> | null>(null);
  const [confirmar, setConfirmar] = useState<RunCard | null>(null);
  const [execucao, setExecucao] = useState<RunExecution | null>(null);

  const containers = useQuery({
    queryKey: ["runmanager", "containers", modoEdicao],
    queryFn: () => runManagerApi.containers(modoEdicao),
  });
  const catalogo = useQuery({
    queryKey: ["runmanager", "catalog"],
    queryFn: runManagerApi.catalog,
  });

  const atualizar = () =>
    void queryClient.invalidateQueries({ queryKey: ["runmanager", "containers"] });

  const salvarContainer = useMutation({
    mutationFn: () => runManagerApi.saveContainer(container as RunContainer),
    onSuccess: () => {
      setContainer(null);
      atualizar();
    },
  });

  const salvarCard = useMutation({
    mutationFn: () => runManagerApi.saveCard(card as RunCard),
    onSuccess: () => {
      setCard(null);
      atualizar();
    },
  });

  const excluirContainer = useMutation({
    mutationFn: (id: number) => runManagerApi.removeContainer(id),
    onSuccess: atualizar,
  });

  const excluirCard = useMutation({
    mutationFn: (id: number) => runManagerApi.removeCard(id),
    onSuccess: atualizar,
  });

  const disparar = useMutation({
    mutationFn: (id: number) => runManagerApi.runCard(id),
    onSuccess: (iniciada) => {
      setConfirmar(null);
      setExecucao(iniciada);
      void queryClient.invalidateQueries({ queryKey: ["runmanager", "executions"] });
    },
    onError: (erro: Error) => {
      setConfirmar(null);
      notifications.show({
        color: "red",
        title: "Não foi possível disparar",
        message: erro.message,
      });
    },
  });

  const clicar = (c: RunCard) => {
    if (c.production) setConfirmar(c);
    else disparar.mutate(c.id!);
  };

  const lista = containers.data ?? [];
  const sistemas = catalogo.data?.systems ?? [];
  const ambientes = catalogo.data?.environments ?? [];
  const tags = catalogo.data?.tags ?? [];

  const versoesDoSistema = (systemId?: number | null) =>
    sistemas.find((s) => s.id === systemId)?.versions ?? [];

  return (
    <Stack gap="lg">
      <Group justify="space-between" align="flex-end" wrap="wrap">
        <div>
          <Title order={4}>Execuções</Title>
          <Text size="xs" c="dimmed">
            {modoEdicao
              ? "Cadastro dos blocos e botões que aparecem no disparo."
              : "Clique num botão para rodar. Produção pede confirmação."}
          </Text>
        </div>
        {modoEdicao && (
          <Button
            onClick={() =>
              setContainer({ title: "", description: "", active: true, position: lista.length })
            }
          >
            Adicionar container
          </Button>
        )}
      </Group>

      {lista.length === 0 ? (
        <Alert variant="light" color="blue">
          Nenhum container ainda. {modoEdicao
            ? "Crie um e coloque dentro os botões de disparo."
            : "Abra o cadastro para criar o primeiro."}
        </Alert>
      ) : (
        <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="md">
          {lista.map((c) => (
            <div key={c.id} className="run-container" data-inactive={!c.active}>
              <Group justify="space-between" align="flex-start" wrap="nowrap" mb="sm">
                <div>
                  <Text className="run-container-title" size="sm">
                    {c.title}
                  </Text>
                  {c.description && (
                    <Text size="xs" c="dimmed">
                      {c.description}
                    </Text>
                  )}
                </div>
                {modoEdicao && (
                  <Group gap={4} wrap="nowrap">
                    {!c.active && (
                      <Badge size="xs" color="gray" variant="light">
                        inativo
                      </Badge>
                    )}
                    <Button size="compact-xs" variant="subtle" onClick={() => setContainer(c)}>
                      Editar
                    </Button>
                    <Button
                      size="compact-xs"
                      variant="subtle"
                      onClick={() => setCard(cardVazio(c.id))}
                    >
                      + Card
                    </Button>
                    <Button
                      size="compact-xs"
                      variant="subtle"
                      color="red"
                      onClick={() => excluirContainer.mutate(c.id!)}
                    >
                      Excluir
                    </Button>
                  </Group>
                )}
              </Group>

              {c.cards.length === 0 ? (
                <Text size="xs" c="dimmed">
                  Sem cards.
                </Text>
              ) : (
                <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="xs">
                  {c.cards.map((card2) => (
                    <Stack key={card2.id} gap={4}>
                      <button
                        type="button"
                        className="run-card"
                        data-env={card2.production ? "prod" : "hml"}
                        disabled={disparar.isPending || !card2.active}
                        onClick={() => clicar(card2)}
                        title={card2.script}
                      >
                        {card2.title}
                      </button>
                      {modoEdicao && (
                        <Group gap={4} justify="space-between">
                          <Text size="10px" c="dimmed" lineClamp={1} style={{ flex: 1 }}>
                            {card2.script}
                          </Text>
                          <Group gap={2} wrap="nowrap">
                            <Button
                              size="compact-xs"
                              variant="subtle"
                              onClick={() => setCard(card2)}
                            >
                              Editar
                            </Button>
                            <Button
                              size="compact-xs"
                              variant="subtle"
                              color="red"
                              onClick={() => excluirCard.mutate(card2.id!)}
                            >
                              Excluir
                            </Button>
                          </Group>
                        </Group>
                      )}
                    </Stack>
                  ))}
                </SimpleGrid>
              )}
            </div>
          ))}
        </SimpleGrid>
      )}

      {execucao && (
        <RunTerminal
          execucao={execucao}
          onEstado={(estado) => {
            if (estado !== "RUNNING") {
              void queryClient.invalidateQueries({ queryKey: ["runmanager", "executions"] });
              void queryClient.invalidateQueries({ queryKey: ["runmanager", "failures"] });
            }
          }}
        />
      )}

      {/* ----------------------------------------------------------- container */}
      <Modal
        opened={!!container}
        onClose={() => setContainer(null)}
        title={container?.id ? "Editar container" : "Novo container"}
      >
        {container && (
          <Stack gap="md">
            <TextInput
              label="Título"
              placeholder="Smoke V2"
              required
              value={container.title ?? ""}
              onChange={(e) => setContainer({ ...container, title: e.currentTarget.value })}
            />
            <Textarea
              label="Descrição"
              placeholder="Fumaça — o essencial de cada módulo"
              autosize
              minRows={2}
              value={container.description ?? ""}
              onChange={(e) =>
                setContainer({ ...container, description: e.currentTarget.value })
              }
            />
            <Switch
              label="Ativo"
              description="Inativo some do disparo, mas o histórico continua apontando para ele."
              checked={container.active ?? true}
              onChange={(e) => setContainer({ ...container, active: e.currentTarget.checked })}
            />
            {salvarContainer.isError && (
              <Text size="sm" c="red">
                {(salvarContainer.error as Error).message}
              </Text>
            )}
            <Group justify="flex-end">
              <Button variant="subtle" onClick={() => setContainer(null)}>
                Cancelar
              </Button>
              <Button
                loading={salvarContainer.isPending}
                disabled={!container.title?.trim()}
                onClick={() => salvarContainer.mutate()}
              >
                Salvar
              </Button>
            </Group>
          </Stack>
        )}
      </Modal>

      {/* ---------------------------------------------------------------- card */}
      <Modal
        opened={!!card}
        onClose={() => setCard(null)}
        title={card?.id ? "Editar card" : "Novo card"}
        size="lg"
      >
        {card && (
          <Stack gap="md">
            <TextInput
              label="Título"
              placeholder="HML"
              required
              value={card.title ?? ""}
              onChange={(e) => setCard({ ...card, title: e.currentTarget.value })}
            />
            <Group grow align="flex-start">
              <Select
                label="Ambiente"
                placeholder="Escolha"
                clearable
                data={ambientes
                  .filter((a) => a.active)
                  .map((a) => ({ value: String(a.id), label: a.name }))}
                value={card.environmentId ? String(card.environmentId) : null}
                onChange={(v) => setCard({ ...card, environmentId: v ? Number(v) : null })}
              />
              <Select
                label="Sistema"
                description="Define onde o script roda."
                placeholder="Escolha"
                clearable
                data={sistemas
                  .filter((s) => s.active)
                  .map((s) => ({ value: String(s.id), label: s.name }))}
                value={card.systemId ? String(card.systemId) : null}
                onChange={(v) =>
                  setCard({ ...card, systemId: v ? Number(v) : null, versionId: null })
                }
              />
            </Group>
            <Group grow>
              <Select
                label="Versão"
                placeholder="Opcional"
                clearable
                data={versoesDoSistema(card.systemId).map((v) => ({
                  value: String(v.id),
                  label: v.name,
                }))}
                value={card.versionId ? String(card.versionId) : null}
                onChange={(v) => setCard({ ...card, versionId: v ? Number(v) : null })}
              />
              <Select
                label="Marcação"
                placeholder="Opcional"
                clearable
                data={tags
                  .filter((t) => t.active)
                  .map((t) => ({ value: String(t.id), label: t.name }))}
                value={card.tagId ? String(card.tagId) : null}
                onChange={(v) => setCard({ ...card, tagId: v ? Number(v) : null })}
              />
            </Group>
            <Textarea
              label="Script"
              description="O comando que será executado, como você digitaria no terminal."
              placeholder="npx playwright test --grep @smoke --project=v2"
              required
              autosize
              minRows={2}
              styles={{ input: { fontFamily: "monospace", fontSize: 12.5 } }}
              value={card.script ?? ""}
              onChange={(e) => setCard({ ...card, script: e.currentTarget.value })}
            />
            <Switch
              label="Ativo"
              checked={card.active ?? true}
              onChange={(e) => setCard({ ...card, active: e.currentTarget.checked })}
            />
            {salvarCard.isError && (
              <Text size="sm" c="red">
                {(salvarCard.error as Error).message}
              </Text>
            )}
            <Group justify="flex-end">
              <Button variant="subtle" onClick={() => setCard(null)}>
                Cancelar
              </Button>
              <Button
                loading={salvarCard.isPending}
                disabled={!card.title?.trim() || !card.script?.trim()}
                onClick={() => salvarCard.mutate()}
              >
                Salvar
              </Button>
            </Group>
          </Stack>
        )}
      </Modal>

      {/* ------------------------------------------------------- confirmação */}
      <Modal
        opened={!!confirmar}
        onClose={() => setConfirmar(null)}
        title="Rodar em produção"
      >
        <Stack gap="md">
          <Alert color="red" variant="light">
            <strong>{confirmar?.title}</strong> aponta para um ambiente marcado como
            produção. O teste vai mexer em dados reais.
          </Alert>
          <Paper p="xs" withBorder radius="md">
            <Text size="xs" style={{ fontFamily: "monospace" }}>
              {confirmar?.script}
            </Text>
          </Paper>
          <Group justify="flex-end">
            <Button variant="subtle" onClick={() => setConfirmar(null)}>
              Cancelar
            </Button>
            <Button
              color="red"
              loading={disparar.isPending}
              onClick={() => disparar.mutate(confirmar!.id!)}
            >
              Rodar mesmo assim
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  );
}

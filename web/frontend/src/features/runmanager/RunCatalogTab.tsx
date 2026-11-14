import { useState } from "react";
import {
  Accordion,
  Alert,
  Badge,
  Button,
  Group,
  Modal,
  Stack,
  Switch,
  Table,
  Text,
  TextInput,
  Textarea,
} from "@mantine/core";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { runManagerApi } from "../../services/api/runManagerApi";
import { SeletorDePasta } from "./SeletorDePasta";
import type {
  RunEnvironment,
  RunSystem,
  RunTag,
  RunVersion,
} from "../../types/runmanager.types";

/**
 * Cadastro do RunManager.
 *
 * Sistemas, versões, ambientes e marcações — o que o painel antigo trazia
 * escrito no código. O identificador é derivado do nome e entra no comando; ele
 * aparece na tela para que ninguém precise adivinhar o que vai ser executado.
 */
export function RunCatalogTab() {
  const queryClient = useQueryClient();
  const [sistema, setSistema] = useState<Partial<RunSystem> | null>(null);
  const [versao, setVersao] = useState<Partial<RunVersion> | null>(null);
  const [ambiente, setAmbiente] = useState<Partial<RunEnvironment> | null>(null);
  const [tag, setTag] = useState<Partial<RunTag> | null>(null);

  const catalogo = useQuery({
    queryKey: ["runmanager", "catalog"],
    queryFn: runManagerApi.catalog,
  });

  const atualizar = () => {
    void queryClient.invalidateQueries({ queryKey: ["runmanager", "catalog"] });
    void queryClient.invalidateQueries({ queryKey: ["runmanager", "containers"] });
  };

  const salvarSistema = useMutation({
    mutationFn: () => runManagerApi.saveSystem(sistema as RunSystem),
    onSuccess: () => {
      setSistema(null);
      atualizar();
    },
  });
  const salvarVersao = useMutation({
    mutationFn: () => runManagerApi.saveVersion(versao as RunVersion),
    onSuccess: () => {
      setVersao(null);
      atualizar();
    },
  });
  const salvarAmbiente = useMutation({
    mutationFn: () => runManagerApi.saveEnvironment(ambiente as RunEnvironment),
    onSuccess: () => {
      setAmbiente(null);
      atualizar();
    },
  });
  const salvarTag = useMutation({
    mutationFn: () => runManagerApi.saveTag(tag as RunTag),
    onSuccess: () => {
      setTag(null);
      atualizar();
    },
  });

  const excluirSistema = useMutation({
    mutationFn: (id: number) => runManagerApi.removeSystem(id),
    onSuccess: atualizar,
  });
  const excluirVersao = useMutation({
    mutationFn: (id: number) => runManagerApi.removeVersion(id),
    onSuccess: atualizar,
  });
  const excluirAmbiente = useMutation({
    mutationFn: (id: number) => runManagerApi.removeEnvironment(id),
    onSuccess: atualizar,
  });
  const excluirTag = useMutation({
    mutationFn: (id: number) => runManagerApi.removeTag(id),
    onSuccess: atualizar,
  });

  const dados = catalogo.data;

  return (
    <Stack gap="lg">
      <Accordion multiple defaultValue={["sistemas"]} variant="separated">
        {/* --------------------------------------------------------- sistemas */}
        <Accordion.Item value="sistemas">
          <Accordion.Control>
            <Group gap="xs">
              <Text fw={600} size="sm">
                Sistemas e versões
              </Text>
              <Badge size="xs" variant="light">
                {dados?.systems.length ?? 0}
              </Badge>
            </Group>
          </Accordion.Control>
          <Accordion.Panel>
            <Stack gap="sm">
              <Group justify="flex-end">
                <Button
                  size="xs"
                  onClick={() =>
                    setSistema({ name: "", active: true, versions: [], position: 0 })
                  }
                >
                  Novo sistema
                </Button>
              </Group>
              {(dados?.systems ?? []).length === 0 ? (
                <Alert variant="light" color="blue">
                  Cadastre o sistema que tem automação. É dele que sai onde o script roda
                  e o que a leitura do projeto vasculha.
                </Alert>
              ) : (
                <Table striped highlightOnHover>
                  <Table.Thead>
                    <Table.Tr>
                      <Table.Th>Sistema</Table.Th>
                      <Table.Th>Onde vive</Table.Th>
                      <Table.Th>Versões</Table.Th>
                      <Table.Th />
                    </Table.Tr>
                  </Table.Thead>
                  <Table.Tbody>
                    {(dados?.systems ?? []).map((s) => (
                      <Table.Tr key={s.id}>
                        <Table.Td>
                          <Group gap="xs">
                            <Text size="sm" fw={600}>
                              {s.name}
                            </Text>
                            <Badge size="xs" variant="light">
                              {s.key}
                            </Badge>
                            {!s.active && (
                              <Badge size="xs" color="gray" variant="light">
                                inativo
                              </Badge>
                            )}
                          </Group>
                        </Table.Td>
                        <Table.Td>
                          <Text size="xs" c="dimmed">
                            {s.localPath || s.repository || "—"}
                          </Text>
                        </Table.Td>
                        <Table.Td>
                          <Group gap={4}>
                            {s.versions.map((v) => (
                              <Badge
                                key={v.id}
                                size="xs"
                                variant="light"
                                style={{ cursor: "pointer" }}
                                onClick={() => setVersao(v)}
                              >
                                {v.name}
                              </Badge>
                            ))}
                            <Button
                              size="compact-xs"
                              variant="subtle"
                              onClick={() =>
                                setVersao({ systemId: s.id, name: "", active: true, position: 0 })
                              }
                            >
                              +
                            </Button>
                          </Group>
                        </Table.Td>
                        <Table.Td>
                          <Group gap={4} justify="flex-end" wrap="nowrap">
                            <Button size="compact-xs" variant="subtle" onClick={() => setSistema(s)}>
                              Editar
                            </Button>
                            <Button
                              size="compact-xs"
                              variant="subtle"
                              color="red"
                              onClick={() => excluirSistema.mutate(s.id!)}
                            >
                              Excluir
                            </Button>
                          </Group>
                        </Table.Td>
                      </Table.Tr>
                    ))}
                  </Table.Tbody>
                </Table>
              )}
            </Stack>
          </Accordion.Panel>
        </Accordion.Item>

        {/* -------------------------------------------------------- ambientes */}
        <Accordion.Item value="ambientes">
          <Accordion.Control>
            <Group gap="xs">
              <Text fw={600} size="sm">
                Ambientes
              </Text>
              <Badge size="xs" variant="light">
                {dados?.environments.length ?? 0}
              </Badge>
            </Group>
          </Accordion.Control>
          <Accordion.Panel>
            <Stack gap="sm">
              <Group justify="flex-end">
                <Button
                  size="xs"
                  onClick={() =>
                    setAmbiente({ name: "", production: false, active: true, position: 0 })
                  }
                >
                  Novo ambiente
                </Button>
              </Group>
              <Group gap="xs" wrap="wrap">
                {(dados?.environments ?? []).map((a) => (
                  <Badge
                    key={a.id}
                    size="lg"
                    variant="light"
                    color={a.production ? "red" : "teal"}
                    style={{ cursor: "pointer", opacity: a.active ? 1 : 0.5 }}
                    onClick={() => setAmbiente(a)}
                    rightSection={
                      <span
                        role="button"
                        aria-label={"Excluir " + a.name}
                        style={{ cursor: "pointer" }}
                        onClick={(e) => {
                          e.stopPropagation();
                          excluirAmbiente.mutate(a.id!);
                        }}
                      >
                        ×
                      </span>
                    }
                  >
                    {a.name}
                  </Badge>
                ))}
              </Group>
              <Text size="xs" c="dimmed">
                Ambiente marcado como produção fica vermelho no painel e pede confirmação
                antes de rodar.
              </Text>
            </Stack>
          </Accordion.Panel>
        </Accordion.Item>

        {/* ------------------------------------------------------------ tags */}
        <Accordion.Item value="tags">
          <Accordion.Control>
            <Group gap="xs">
              <Text fw={600} size="sm">
                Marcações
              </Text>
              <Badge size="xs" variant="light">
                {dados?.tags.length ?? 0}
              </Badge>
            </Group>
          </Accordion.Control>
          <Accordion.Panel>
            <Stack gap="sm">
              <Group justify="flex-end">
                <Button
                  size="xs"
                  onClick={() => setTag({ name: "", active: true, position: 0 })}
                >
                  Nova marcação
                </Button>
              </Group>
              <Group gap="xs" wrap="wrap">
                {(dados?.tags ?? []).map((t) => (
                  <Badge
                    key={t.id}
                    size="lg"
                    variant="light"
                    style={{ cursor: "pointer", opacity: t.active ? 1 : 0.5 }}
                    onClick={() => setTag(t)}
                    rightSection={
                      <span
                        role="button"
                        aria-label={"Excluir " + t.name}
                        style={{ cursor: "pointer" }}
                        onClick={(e) => {
                          e.stopPropagation();
                          excluirTag.mutate(t.id!);
                        }}
                      >
                        ×
                      </span>
                    }
                  >
                    @{t.key ?? t.name}
                  </Badge>
                ))}
              </Group>
            </Stack>
          </Accordion.Panel>
        </Accordion.Item>
      </Accordion>

      {/* --------------------------------------------------------- formulários */}
      <Modal
        opened={!!sistema}
        onClose={() => setSistema(null)}
        title={sistema?.id ? "Editar sistema" : "Novo sistema"}
        size="lg"
      >
        {sistema && (
          <Stack gap="md">
            <TextInput
              label="Nome"
              placeholder="Ultracar"
              required
              value={sistema.name ?? ""}
              onChange={(e) => setSistema({ ...sistema, name: e.currentTarget.value })}
            />
            <SeletorDePasta
              label="Caminho do projeto nesta máquina"
              description="Onde ficam a pasta tests e o playwright.config."
              placeholder="F:\\FUSAO\\playwright"
              value={sistema.localPath ?? ""}
              onChange={(caminho) => setSistema({ ...sistema, localPath: caminho })}
            />
            <Group grow>
              <TextInput
                label="Repositório"
                description="Para quando o disparo for pela pipeline."
                placeholder="workspace/repositorio"
                value={sistema.repository ?? ""}
                onChange={(e) => setSistema({ ...sistema, repository: e.currentTarget.value })}
              />
              <TextInput
                label="Branch"
                placeholder="master"
                value={sistema.branch ?? ""}
                onChange={(e) => setSistema({ ...sistema, branch: e.currentTarget.value })}
              />
            </Group>
            <Textarea
              label="Comando da seleção de casos"
              description="{{casos}} vira a lista de casos marcados. Em branco, usa o padrão do Playwright."
              placeholder="npx playwright test {{casos}}"
              autosize
              minRows={2}
              styles={{ input: { fontFamily: "monospace", fontSize: 12.5 } }}
              value={sistema.selectionScript ?? ""}
              onChange={(e) =>
                setSistema({ ...sistema, selectionScript: e.currentTarget.value })
              }
            />
            <TextInput
              label="Como o comando escolhe a versão"
              description={
                "{{versao}} vira o identificador da versão. Em branco, usa " +
                "--project={{versao}}, como o Playwright. Escreva \"nenhum\" para " +
                "resolver a versão dentro do script do card."
              }
              placeholder="--project={{versao}}"
              styles={{ input: { fontFamily: "monospace", fontSize: 12.5 } }}
              value={sistema.versionFlag ?? ""}
              onChange={(e) => setSistema({ ...sistema, versionFlag: e.currentTarget.value })}
            />
            <Textarea
              label="Variáveis fixas deste sistema"
              description="Valem para toda execução dele. O ambiente é aplicado depois e vence em caso de repetição."
              placeholder="TEST_USER=usuario@exemplo.com"
              autosize
              minRows={2}
              styles={{ input: { fontFamily: "monospace", fontSize: 12.5 } }}
              value={sistema.variables ?? ""}
              onChange={(e) => setSistema({ ...sistema, variables: e.currentTarget.value })}
            />
            <Switch
              label="Ativo"
              checked={sistema.active ?? true}
              onChange={(e) => setSistema({ ...sistema, active: e.currentTarget.checked })}
            />
            {salvarSistema.isError && (
              <Text size="sm" c="red">
                {(salvarSistema.error as Error).message}
              </Text>
            )}
            <Group justify="flex-end">
              <Button variant="subtle" onClick={() => setSistema(null)}>
                Cancelar
              </Button>
              <Button
                loading={salvarSistema.isPending}
                disabled={!sistema.name?.trim()}
                onClick={() => salvarSistema.mutate()}
              >
                Salvar
              </Button>
            </Group>
          </Stack>
        )}
      </Modal>

      <Modal
        opened={!!versao}
        onClose={() => setVersao(null)}
        title={versao?.id ? "Editar versão" : "Nova versão"}
      >
        {versao && (
          <Stack gap="md">
            <TextInput
              label="Nome"
              placeholder="V2"
              required
              value={versao.name ?? ""}
              onChange={(e) => setVersao({ ...versao, name: e.currentTarget.value })}
            />
            <Switch
              label="Ativa"
              checked={versao.active ?? true}
              onChange={(e) => setVersao({ ...versao, active: e.currentTarget.checked })}
            />
            <Group justify="space-between">
              {versao.id ? (
                <Button
                  variant="subtle"
                  color="red"
                  onClick={() => {
                    excluirVersao.mutate(versao.id!);
                    setVersao(null);
                  }}
                >
                  Excluir
                </Button>
              ) : (
                <span />
              )}
              <Group gap="sm">
                <Button variant="subtle" onClick={() => setVersao(null)}>
                  Cancelar
                </Button>
                <Button
                  loading={salvarVersao.isPending}
                  disabled={!versao.name?.trim()}
                  onClick={() => salvarVersao.mutate()}
                >
                  Salvar
                </Button>
              </Group>
            </Group>
          </Stack>
        )}
      </Modal>

      <Modal
        opened={!!ambiente}
        onClose={() => setAmbiente(null)}
        title={ambiente?.id ? "Editar ambiente" : "Novo ambiente"}
      >
        {ambiente && (
          <Stack gap="md">
            <TextInput
              label="Nome"
              placeholder="Homologação"
              required
              value={ambiente.name ?? ""}
              onChange={(e) => setAmbiente({ ...ambiente, name: e.currentTarget.value })}
            />
            <Switch
              label="É produção"
              description="Fica vermelho no painel e pede confirmação antes de rodar."
              checked={ambiente.production ?? false}
              onChange={(e) =>
                setAmbiente({ ...ambiente, production: e.currentTarget.checked })
              }
            />
            <Textarea
              label="Variáveis deste ambiente"
              description={
                "É isto que troca a URL: o projeto de automação lê estas variáveis do " +
                "processo. Uma por linha, CHAVE=valor. Em branco, vale ENV=" +
                (ambiente.key ?? "identificador") + "."
              }
              placeholder={"ENV=" + (ambiente.key ?? "hml")}
              autosize
              minRows={3}
              styles={{ input: { fontFamily: "monospace", fontSize: 12.5 } }}
              value={ambiente.variables ?? ""}
              onChange={(e) => setAmbiente({ ...ambiente, variables: e.currentTarget.value })}
            />
            <Switch
              label="Ativo"
              checked={ambiente.active ?? true}
              onChange={(e) => setAmbiente({ ...ambiente, active: e.currentTarget.checked })}
            />
            {salvarAmbiente.isError && (
              <Text size="sm" c="red">
                {(salvarAmbiente.error as Error).message}
              </Text>
            )}
            <Group justify="flex-end">
              <Button variant="subtle" onClick={() => setAmbiente(null)}>
                Cancelar
              </Button>
              <Button
                loading={salvarAmbiente.isPending}
                disabled={!ambiente.name?.trim()}
                onClick={() => salvarAmbiente.mutate()}
              >
                Salvar
              </Button>
            </Group>
          </Stack>
        )}
      </Modal>

      <Modal
        opened={!!tag}
        onClose={() => setTag(null)}
        title={tag?.id ? "Editar marcação" : "Nova marcação"}
      >
        {tag && (
          <Stack gap="md">
            <TextInput
              label="Nome"
              placeholder="Smoke"
              required
              value={tag.name ?? ""}
              onChange={(e) => setTag({ ...tag, name: e.currentTarget.value })}
            />
            <TextInput
              label="Descrição"
              placeholder="O essencial de cada módulo"
              value={tag.description ?? ""}
              onChange={(e) => setTag({ ...tag, description: e.currentTarget.value })}
            />
            <Switch
              label="Ativa"
              checked={tag.active ?? true}
              onChange={(e) => setTag({ ...tag, active: e.currentTarget.checked })}
            />
            {salvarTag.isError && (
              <Text size="sm" c="red">
                {(salvarTag.error as Error).message}
              </Text>
            )}
            <Group justify="flex-end">
              <Button variant="subtle" onClick={() => setTag(null)}>
                Cancelar
              </Button>
              <Button
                loading={salvarTag.isPending}
                disabled={!tag.name?.trim()}
                onClick={() => salvarTag.mutate()}
              >
                Salvar
              </Button>
            </Group>
          </Stack>
        )}
      </Modal>
    </Stack>
  );
}

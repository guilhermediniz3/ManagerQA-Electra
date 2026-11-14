import { useMemo, useState } from "react";
import {
  Alert,
  Badge,
  Button,
  Checkbox,
  Group,
  Modal,
  Paper,
  ScrollArea,
  Select,
  SimpleGrid,
  Stack,
  Text,
  TextInput,
  Textarea,
  Title,
} from "@mantine/core";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { notifications } from "@mantine/notifications";
import { runManagerApi } from "../../services/api/runManagerApi";
import type { RunExecution, RunManualCase } from "../../types/runmanager.types";
import { RunTerminal } from "./RunTerminal";

/**
 * Casos de teste por módulo, e a execução de uma seleção livre.
 *
 * A lista mistura o que foi lido do projeto com o que foi cadastrado à mão —
 * nem todo caso vive no repositório, e quem monta uma rodada quer marcar os dois
 * na mesma tela. A releitura é um botão, e não algo que acontece ao abrir:
 * varrer o projeto leva segundos e ninguém pediu para esperar.
 */
export function RunCasesTab() {
  const queryClient = useQueryClient();
  const [sistemaId, setSistemaId] = useState<number | null>(null);
  const [moduloAberto, setModuloAberto] = useState<string | null>(null);
  const [marcados, setMarcados] = useState<string[]>([]);
  const [filtro, setFiltro] = useState("");
  const [ambienteId, setAmbienteId] = useState<number | null>(null);
  const [manual, setManual] = useState<Partial<RunManualCase> | null>(null);
  const [execucao, setExecucao] = useState<RunExecution | null>(null);

  const catalogo = useQuery({
    queryKey: ["runmanager", "catalog"],
    queryFn: runManagerApi.catalog,
  });

  const sistemas = catalogo.data?.systems.filter((s) => s.active) ?? [];
  const escolhido = sistemaId ?? sistemas[0]?.id ?? null;

  const leitura = useQuery({
    queryKey: ["runmanager", "project", escolhido],
    queryFn: () => runManagerApi.project(escolhido!),
    enabled: !!escolhido,
  });

  const reler = useMutation({
    mutationFn: () => runManagerApi.scan(escolhido!),
    onSuccess: (novo) => {
      queryClient.setQueryData(["runmanager", "project", escolhido], novo);
      notifications.show({
        color: "green",
        message: novo.totalTests + " caso(s) em " + novo.totalModules + " módulo(s).",
      });
    },
    onError: (erro: Error) =>
      notifications.show({ color: "red", title: "Não foi possível ler", message: erro.message }),
  });

  const salvarManual = useMutation({
    mutationFn: () => runManagerApi.saveManualCase(manual as RunManualCase),
    onSuccess: () => {
      setManual(null);
      void queryClient.invalidateQueries({ queryKey: ["runmanager", "project", escolhido] });
    },
  });

  const disparar = useMutation({
    mutationFn: () =>
      runManagerApi.runSelection({
        systemId: escolhido!,
        environmentId: ambienteId,
        title: "Seleção de " + marcados.length + " caso(s)",
        caseIds: marcados,
      }),
    onSuccess: (iniciada) => {
      setExecucao(iniciada);
      setMarcados([]);
      void queryClient.invalidateQueries({ queryKey: ["runmanager", "executions"] });
    },
    onError: (erro: Error) =>
      notifications.show({ color: "red", title: "Não foi possível rodar", message: erro.message }),
  });

  const modulos = leitura.data?.modules ?? [];
  const modulo = modulos.find((m) => m.key === moduloAberto) ?? modulos[0];

  const filtrados = useMemo(() => {
    const termo = filtro.trim().toLowerCase();
    if (!termo) return modulos;
    return modulos
      .map((m) => ({
        ...m,
        tests: m.tests.filter(
          (t) =>
            t.title.toLowerCase().includes(termo) ||
            (t.code ?? "").toLowerCase().includes(termo) ||
            t.tags.some((tag) => tag.includes(termo)),
        ),
      }))
      .filter((m) => m.tests.length > 0 || m.name.toLowerCase().includes(termo));
  }, [modulos, filtro]);

  const alternar = (id: string) =>
    setMarcados((atual) =>
      atual.includes(id) ? atual.filter((x) => x !== id) : [...atual, id],
    );

  return (
    <Stack gap="lg">
      <Group justify="space-between" align="flex-end" wrap="wrap">
        <div>
          <Title order={4}>Casos de teste por módulo</Title>
          <Text size="xs" c="dimmed">
            {leitura.data
              ? leitura.data.totalTests +
                " casos em " +
                leitura.data.totalModules +
                " módulos" +
                (leitura.data.scannedBy ? " · lido por " + leitura.data.scannedBy : "")
              : "Escolha um sistema para ver os casos."}
          </Text>
        </div>
        <Group gap="sm" wrap="wrap">
          <Select
            placeholder="Sistema"
            data={sistemas.map((s) => ({ value: String(s.id), label: s.name }))}
            value={escolhido ? String(escolhido) : null}
            onChange={(v) => {
              setSistemaId(v ? Number(v) : null);
              setMarcados([]);
              setModuloAberto(null);
            }}
          />
          <TextInput
            placeholder="Filtrar módulo, CT ou tag…"
            value={filtro}
            onChange={(e) => setFiltro(e.currentTarget.value)}
          />
          <Button
            variant="default"
            loading={reler.isPending}
            disabled={!escolhido}
            onClick={() => reler.mutate()}
          >
            Atualizar leitura do projeto
          </Button>
          <Button
            variant="light"
            disabled={!escolhido}
            onClick={() =>
              setManual({ systemId: escolhido!, title: "", active: true, moduleKey: modulo?.key })
            }
          >
            Cadastrar caso
          </Button>
        </Group>
      </Group>

      {!escolhido ? (
        <Alert variant="light" color="blue">
          Cadastre um sistema primeiro, com o caminho do projeto de automação.
        </Alert>
      ) : modulos.length === 0 ? (
        <Alert variant="light" color="blue">
          Nada lido ainda. Use “Atualizar leitura do projeto” para varrer a pasta de testes,
          ou cadastre um caso à mão.
        </Alert>
      ) : (
        <SimpleGrid cols={{ base: 1, md: 3 }} spacing="md">
          {/* módulos */}
          <Paper p="md" className="surface">
            <Text size="xs" fw={700} tt="uppercase" mb="xs">
              Módulos
            </Text>
            <ScrollArea.Autosize mah={520}>
              <Stack gap={4}>
                {filtrados.map((m) => (
                  <Button
                    key={m.key}
                    variant={m.key === modulo?.key ? "light" : "subtle"}
                    justify="space-between"
                    size="sm"
                    onClick={() => setModuloAberto(m.key)}
                    rightSection={
                      <Badge size="xs" variant="light">
                        {m.tests.length}
                      </Badge>
                    }
                  >
                    <Group gap={6} wrap="nowrap">
                      {m.version && (
                        <Badge size="xs" variant="outline">
                          {m.version}
                        </Badge>
                      )}
                      <Text size="sm" lineClamp={1}>
                        {m.name}
                      </Text>
                    </Group>
                  </Button>
                ))}
              </Stack>
            </ScrollArea.Autosize>
          </Paper>

          {/* casos do módulo */}
          <Paper p="md" className="surface">
            <Group justify="space-between" mb="xs">
              <Group gap="xs">
                <Text size="xs" fw={700} tt="uppercase">
                  {modulo?.name ?? "—"}
                </Text>
                {modulo?.version && (
                  <Badge size="xs" variant="light">
                    {modulo.version}
                  </Badge>
                )}
              </Group>
              {modulo && (
                <Button
                  size="compact-xs"
                  variant="subtle"
                  onClick={() =>
                    setMarcados((atual) => [
                      ...new Set([...atual, ...modulo.tests.map((t) => t.id)]),
                    ])
                  }
                >
                  Marcar todos
                </Button>
              )}
            </Group>
            <ScrollArea.Autosize mah={520}>
              <Stack gap="xs">
                {(modulo?.tests ?? []).map((caso) => (
                  <Paper key={caso.id} p="xs" withBorder radius="md">
                    <Group gap="xs" wrap="nowrap" align="flex-start">
                      <Checkbox
                        checked={marcados.includes(caso.id)}
                        onChange={() => alternar(caso.id)}
                        aria-label={"Marcar " + caso.title}
                      />
                      <div style={{ flex: 1 }}>
                        <Group gap={6}>
                          {caso.code && (
                            <Badge size="xs" variant="light">
                              {caso.code}
                            </Badge>
                          )}
                          {caso.manual && (
                            <Badge size="xs" color="grape" variant="light">
                              manual
                            </Badge>
                          )}
                        </Group>
                        <Text size="sm">{caso.title}</Text>
                        <Group gap={4}>
                          <Text size="10px" c="dimmed">
                            {caso.file ? caso.file + ":" + caso.line : caso.script}
                          </Text>
                          {caso.tags.map((t) => (
                            <Badge key={t} size="xs" variant="outline">
                              @{t}
                            </Badge>
                          ))}
                        </Group>
                      </div>
                    </Group>
                  </Paper>
                ))}
              </Stack>
            </ScrollArea.Autosize>
          </Paper>

          {/* seleção */}
          <Paper p="md" className="surface">
            <Text size="xs" fw={700} tt="uppercase" mb="xs">
              Seleção
            </Text>
            {marcados.length === 0 ? (
              <Text size="sm" c="dimmed">
                Nenhum caso marcado. Você pode combinar casos de módulos e versões
                diferentes numa execução só.
              </Text>
            ) : (
              <Stack gap="sm">
                <Badge size="lg" variant="light">
                  {marcados.length} caso(s)
                </Badge>
                <Select
                  label="Ambiente"
                  placeholder="Opcional"
                  clearable
                  data={(catalogo.data?.environments ?? [])
                    .filter((a) => a.active)
                    .map((a) => ({ value: String(a.id), label: a.name }))}
                  value={ambienteId ? String(ambienteId) : null}
                  onChange={(v) => setAmbienteId(v ? Number(v) : null)}
                />
                <Button loading={disparar.isPending} onClick={() => disparar.mutate()}>
                  Rodar seleção
                </Button>
                <Button variant="subtle" onClick={() => setMarcados([])}>
                  Limpar seleção
                </Button>
              </Stack>
            )}
          </Paper>
        </SimpleGrid>
      )}

      {execucao && <RunTerminal execucao={execucao} />}

      <Modal
        opened={!!manual}
        onClose={() => setManual(null)}
        title={manual?.id ? "Editar caso" : "Cadastrar caso"}
        size="lg"
      >
        {manual && (
          <Stack gap="md">
            <Group grow>
              <TextInput
                label="Código"
                placeholder="CT-900"
                value={manual.code ?? ""}
                onChange={(e) => setManual({ ...manual, code: e.currentTarget.value })}
              />
              <TextInput
                label="Módulo"
                description="Mesma chave da leitura, como v2/dashboard."
                placeholder="v2/dashboard"
                value={manual.moduleKey ?? ""}
                onChange={(e) => setManual({ ...manual, moduleKey: e.currentTarget.value })}
              />
            </Group>
            <TextInput
              label="Título"
              required
              value={manual.title ?? ""}
              onChange={(e) => setManual({ ...manual, title: e.currentTarget.value })}
            />
            <Textarea
              label="Script"
              description="O que rodar para executar este caso."
              autosize
              minRows={2}
              styles={{ input: { fontFamily: "monospace", fontSize: 12.5 } }}
              value={manual.script ?? ""}
              onChange={(e) => setManual({ ...manual, script: e.currentTarget.value })}
            />
            <TextInput
              label="Tags"
              placeholder="smoke, manual"
              value={manual.tags ?? ""}
              onChange={(e) => setManual({ ...manual, tags: e.currentTarget.value })}
            />
            {salvarManual.isError && (
              <Text size="sm" c="red">
                {(salvarManual.error as Error).message}
              </Text>
            )}
            <Group justify="flex-end">
              <Button variant="subtle" onClick={() => setManual(null)}>
                Cancelar
              </Button>
              <Button
                loading={salvarManual.isPending}
                disabled={!manual.title?.trim()}
                onClick={() => salvarManual.mutate()}
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

import { useEffect, useState } from "react";
import {
  ActionIcon,
  Alert,
  Badge,
  Button,
  Group,
  Modal,
  Paper,
  Select,
  Stack,
  Table,
  Text,
  TextInput,
  Textarea,
  Title,
} from "@mantine/core";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { notifications } from "@mantine/notifications";
import { useNavigate, useParams } from "react-router-dom";
import { regressionApi } from "../../services/api/regressionApi";
import { catalogApi } from "../../services/api/catalogApi";
import type { Regression, RegressionItem } from "../../types/regression.types";
import type { CaseStatus } from "../../types/testCase.types";
import { toApiDate, toInputDate } from "../../utils/dates";

const STATUS: { value: CaseStatus; label: string }[] = [
  { value: "EM_PROGRESSO", label: "Em progresso" },
  { value: "CONCLUIDA", label: "Concluída" },
  { value: "IMPEDIMENTO", label: "Impedimento" },
  { value: "RETORNO", label: "Retorno" },
];

const linhaVazia = (): RegressionItem => ({
  systemModuleId: null,
  description: "",
  observation: "",
  flow: "",
  status: null,
  testerId: null,
});

/**
 * Montagem e execução de uma rodada de regressão.
 *
 * Cada linha é um módulo, com o QA responsável na própria linha: numa rodada os
 * módulos se dividem entre pessoas diferentes, e um responsável único para a
 * rodada inteira não diria quem olhou o quê.
 *
 * "Buscar módulos aprovados" levanta o que passou por demanda concluída no
 * período e acrescenta o que ainda não está na lista — o que já foi preenchido
 * à mão fica como está, porque essa busca serve para poupar digitação e não
 * para substituir o que alguém decidiu.
 */
export function RegressionDetailPage() {
  const { id } = useParams();
  const navegar = useNavigate();
  const queryClient = useQueryClient();

  const [form, setForm] = useState<Regression | null>(null);
  const [buscando, setBuscando] = useState(false);
  const [excluindo, setExcluindo] = useState(false);
  /**
   * Nada é salvo sozinho aqui: quem monta a rodada quer conferir antes. O aviso
   * existe porque, sem ele, a pessoa sai da tela achando que salvou.
   */
  const [naoSalvo, setNaoSalvo] = useState(false);
  const [de, setDe] = useState("");
  const [ate, setAte] = useState("");

  const regressao = useQuery({
    queryKey: ["regression", id],
    queryFn: () => regressionApi.get(Number(id)),
    enabled: !!id,
  });

  const modulos = useQuery({
    queryKey: ["catalog", "modules"],
    queryFn: () => catalogApi.list("modules"),
  });
  const testers = useQuery({
    queryKey: ["catalog", "testers"],
    queryFn: () => catalogApi.list("testers"),
  });

  useEffect(() => {
    if (regressao.data) {
      setForm(regressao.data);
      setNaoSalvo(false);
    }
  }, [regressao.data]);

  /** Toda alteração passa por aqui, e é o que acende o aviso de não salvo. */
  const alterar = (mudanca: Partial<Regression>) => {
    setForm((atual) => (atual ? { ...atual, ...mudanca } : atual));
    setNaoSalvo(true);
  };

  const salvar = useMutation({
    // Quantas linhas foram enviadas: é o que permite dizer, depois, quantas o
    // servidor descartou por estarem em branco.
    mutationFn: async () => {
      const enviados = form!.items.length;
      const salva = await regressionApi.save(form!);
      return { salva, enviados };
    },
    onSuccess: ({ salva, enviados }) => {
      setForm(salva);
      setNaoSalvo(false);
      // Linha sem módulo, sem descrição e sem fluxo não é gravada. Sem dizer
      // isso, ela simplesmente some ao salvar e parece que nada foi guardado.
      const descartadas = enviados - salva.items.length;
      notifications.show({
        color: descartadas > 0 ? "yellow" : "green",
        title: salva.items.length + " módulo(s) salvo(s)",
        message:
          descartadas > 0
            ? descartadas +
              " linha(s) em branco não foram salvas. Escolha o módulo ou escreva a descrição."
            : "Rodada atualizada.",
      });
      void queryClient.invalidateQueries({ queryKey: ["regressions"] });
      void queryClient.invalidateQueries({ queryKey: ["regression", id] });
    },
    onError: (erro: Error) =>
      notifications.show({
        color: "red",
        title: "Não foi possível salvar",
        message: erro.message,
      }),
  });

  const excluir = useMutation({
    mutationFn: () => regressionApi.remove(Number(id)),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["regressions"] });
      navegar("/regressao");
    },
    onError: (erro: Error) =>
      notifications.show({
        color: "red",
        title: "Não foi possível excluir",
        message: erro.message,
      }),
  });

  const clonar = useMutation({
    mutationFn: () => regressionApi.clone(Number(id)),
    onSuccess: (copia) => {
      void queryClient.invalidateQueries({ queryKey: ["regressions"] });
      navegar("/regressao/" + copia.id);
    },
  });

  const aprovados = useMutation({
    mutationFn: () =>
      regressionApi.aprovados({
        de: toApiDate(de) || undefined,
        ate: toApiDate(ate) || undefined,
      }),
    onSuccess: (encontrados) => {
      const jaTem = new Set(
        (form?.items ?? []).map((i) => i.systemModuleId ?? 0),
      );
      const novos = encontrados.filter(
        (i) => i.systemModuleId && !jaTem.has(i.systemModuleId),
      );
      if (novos.length === 0) {
        notifications.show({
          color: "yellow",
          message:
            encontrados.length === 0
              ? "Nenhuma demanda concluída neste período."
              : "Os módulos encontrados já estão na lista.",
        });
        return;
      }
      alterar({ items: [...(form?.items ?? []), ...novos] });
      setBuscando(false);
      notifications.show({
        color: "green",
        message:
          novos.length +
          " módulo(s) acrescentado(s). Revise e salve para guardar.",
      });
    },
  });

  const trocar = (indice: number, mudanca: Partial<RegressionItem>) => {
    const items = [...form!.items];
    items[indice] = { ...items[indice], ...mudanca };
    alterar({ items });
  };

  const remover = (indice: number) =>
    alterar({ items: form!.items.filter((_, i) => i !== indice) });

  if (regressao.isLoading || !form) {
    return (
      <Text size="sm" c="dimmed">
        Carregando…
      </Text>
    );
  }

  if (regressao.isError) {
    return (
      <Stack gap="md" align="flex-start">
        <Text size="sm" c="red">
          Não foi possível abrir esta rodada.
        </Text>
        <Button variant="subtle" onClick={() => navegar("/regressao")}>
          ← Voltar
        </Button>
      </Stack>
    );
  }

  const opcoesModulo = (modulos.data ?? []).map((m) => ({
    value: String(m.id),
    label: m.name,
  }));
  const opcoesTester = (testers.data ?? []).map((t) => ({
    value: String(t.id),
    label: t.name,
  }));

  return (
    <Stack gap="lg">
      <Group justify="space-between" align="center" wrap="wrap" gap="xs">
        <Group gap="xs" wrap="nowrap">
          <Button variant="subtle" size="compact-sm" onClick={() => navegar("/regressao")}>
            ← Regressão
          </Button>
          <Badge variant="light" size="lg">
            {form.items.length} módulo(s)
          </Badge>
          {naoSalvo && (
            <Badge color="yellow" variant="light" size="lg">
              alterações não salvas
            </Badge>
          )}
        </Group>
        <Group gap="xs">
          <Button variant="subtle" color="red" size="compact-sm"
                  onClick={() => setExcluindo(true)}>
            Excluir rodada
          </Button>
          <Button variant="default" size="compact-sm" loading={clonar.isPending}
                  onClick={() => clonar.mutate()}>
            Clonar
          </Button>
          <Button variant="light" size="compact-sm" onClick={() => setBuscando(true)}>
            Buscar aprovados
          </Button>
          <Button variant="default" size="compact-sm"
                  onClick={() => alterar({ items: [...form.items, linhaVazia()] })}>
            + Módulo
          </Button>
          <Button size="compact-sm" loading={salvar.isPending} onClick={() => salvar.mutate()}>
            Salvar
          </Button>
        </Group>
      </Group>

      {/*
        Nome, data e observação numa faixa só: eles se leem de relance e são
        mexidos uma vez por rodada, enquanto a tabela é onde o trabalho
        acontece — e era ela que estava espremida.
      */}
      <Paper p="sm" className="surface">
        <Group gap="sm" align="flex-end" wrap="wrap">
          <TextInput
            label="Nome"
            size="xs"
            style={{ flex: 2, minWidth: 200 }}
            value={form.name}
            onChange={(e) => alterar({ name: e.currentTarget.value })}
          />
          <TextInput
            type="date"
            label="Data"
            size="xs"
            w={150}
            value={toInputDate(form.data ?? "")}
            onChange={(e) => alterar({ data: toApiDate(e.target.value) })}
          />
          <TextInput
            label="Observação da rodada"
            size="xs"
            style={{ flex: 3, minWidth: 220 }}
            value={form.observation ?? ""}
            onChange={(e) => alterar({ observation: e.currentTarget.value })}
          />
        </Group>
      </Paper>

      <Paper p="sm" className="surface">
        {form.items.length === 0 ? (
          <Text size="sm" c="dimmed">
            Nenhum módulo ainda. Busque os aprovados do período ou acrescente à
            mão.
          </Text>
        ) : (
          <Table.ScrollContainer minWidth={980} type="native">
          <Table striped highlightOnHover verticalSpacing={4} horizontalSpacing={6}>
            <Table.Thead>
              <Table.Tr>
                <Table.Th style={{ minWidth: 150 }}>Módulo</Table.Th>
                <Table.Th style={{ minWidth: 200 }}>Descrição</Table.Th>
                <Table.Th style={{ minWidth: 170 }}>Fluxo</Table.Th>
                <Table.Th style={{ minWidth: 170 }}>Observação</Table.Th>
                <Table.Th style={{ minWidth: 130 }}>Status</Table.Th>
                <Table.Th style={{ minWidth: 130 }}>QA</Table.Th>
                <Table.Th style={{ width: 44 }} />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {form.items.map((item, indice) => (
                <Table.Tr key={item.id ?? "nova-" + indice}>
                  <Table.Td>
                    <Select
                      searchable
                      size="xs"
                      placeholder="Escolha"
                      data={opcoesModulo}
                      value={
                        item.systemModuleId ? String(item.systemModuleId) : null
                      }
                      onChange={(value) =>
                        trocar(indice, {
                          systemModuleId: value ? Number(value) : null,
                        })
                      }
                    />
                  </Table.Td>
                  <Table.Td>
                    <Textarea
                      autosize
                      size="xs"
                      minRows={1}
                      value={item.description ?? ""}
                      onChange={(e) =>
                        trocar(indice, { description: e.currentTarget.value })
                      }
                    />
                  </Table.Td>
                  <Table.Td>
                    <Textarea
                      autosize
                      size="xs"
                      minRows={1}
                      placeholder="Caminho a percorrer"
                      value={item.flow ?? ""}
                      onChange={(e) =>
                        trocar(indice, { flow: e.currentTarget.value })
                      }
                    />
                  </Table.Td>
                  <Table.Td>
                    <Textarea
                      autosize
                      size="xs"
                      minRows={1}
                      value={item.observation ?? ""}
                      onChange={(e) =>
                        trocar(indice, { observation: e.currentTarget.value })
                      }
                    />
                  </Table.Td>
                  <Table.Td>
                    <Select
                      clearable
                      size="xs"
                      placeholder="Pendente"
                      data={STATUS}
                      value={item.status ?? null}
                      onChange={(value) =>
                        trocar(indice, {
                          status: (value as CaseStatus) ?? null,
                        })
                      }
                    />
                  </Table.Td>
                  <Table.Td>
                    <Select
                      searchable
                      clearable
                      size="xs"
                      placeholder="Sem QA"
                      data={opcoesTester}
                      value={item.testerId ? String(item.testerId) : null}
                      onChange={(value) =>
                        trocar(indice, {
                          testerId: value ? Number(value) : null,
                        })
                      }
                    />
                  </Table.Td>
                  <Table.Td>
                    <ActionIcon
                      variant="subtle"
                      color="red"
                      aria-label="Remover este módulo"
                      title="Remover este módulo"
                      onClick={() => remover(indice)}
                    >
                      ×
                    </ActionIcon>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
          </Table.ScrollContainer>
        )}
        {salvar.isError && (
          <Text size="sm" c="red" mt="md">
            {(salvar.error as Error).message}
          </Text>
        )}
      </Paper>

      <Modal
        opened={excluindo}
        onClose={() => setExcluindo(false)}
        title="Excluir rodada"
      >
        <Stack gap="md">
          <Text size="sm">
            Excluir <strong>{form.name}</strong>? Os módulos e o que já foi
            registrado nesta rodada se perdem.
          </Text>
          <Group justify="flex-end" gap="sm">
            <Button variant="subtle" onClick={() => setExcluindo(false)}>
              Cancelar
            </Button>
            <Button color="red" loading={excluir.isPending} onClick={() => excluir.mutate()}>
              Excluir
            </Button>
          </Group>
        </Stack>
      </Modal>

      <Modal
        opened={buscando}
        onClose={() => setBuscando(false)}
        title="Buscar módulos aprovados"
      >
        <Stack gap="md">
          <Alert variant="light" color="blue">
            Procura nos planos de teste concluídos ou em retorno e traz os
            módulos envolvidos. O que já está na lista não é duplicado.
          </Alert>
          <Group gap="md" grow>
            <TextInput
              type="date"
              label="De"
              value={toInputDate(de)}
              onChange={(e) => setDe(toApiDate(e.target.value))}
            />
            <TextInput
              type="date"
              label="Até"
              value={toInputDate(ate)}
              onChange={(e) => setAte(toApiDate(e.target.value))}
            />
          </Group>
          {aprovados.isError && (
            <Text size="sm" c="red">
              {(aprovados.error as Error).message}
            </Text>
          )}
          <Group justify="flex-end" gap="sm">
            <Button variant="subtle" onClick={() => setBuscando(false)}>
              Cancelar
            </Button>
            <Button
              loading={aprovados.isPending}
              onClick={() => aprovados.mutate()}
            >
              Buscar
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  );
}

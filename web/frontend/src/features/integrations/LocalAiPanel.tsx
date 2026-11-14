import {
  Alert,
  Badge,
  Button,
  Group,
  Loader,
  Paper,
  Stack,
  Text,
} from "@mantine/core";
import { notifications } from "@mantine/notifications";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { aiApi } from "../../services/api/aiApi";

/**
 * Gestão do modelo da IA que roda nesta máquina.
 *
 * O aplicativo já vem com um modelo instalado, mas modelo envelhece e máquina
 * varia: numa com folga de memória compensa um maior, e sai versão melhor com
 * o tempo. Trocar precisa ser coisa desta tela — se dependesse de gerar
 * instalador novo, ninguém trocaria.
 *
 * Baixar leva minutos e ocupa vários gigabytes, então o tamanho aparece antes
 * do clique e não depois.
 */
export function LocalAiPanel({
  modelo,
  onEscolher,
}: {
  modelo: string;
  onEscolher: (nome: string) => void;
}) {
  const queryClient = useQueryClient();
  const estado = useQuery({
    queryKey: ["ia-local"],
    queryFn: aiApi.local,
    refetchOnWindowFocus: false,
  });

  const atualizar = () => {
    void queryClient.invalidateQueries({ queryKey: ["ia-local"] });
  };

  const baixar = useMutation({
    mutationFn: aiApi.baixarModeloLocal,
    onSuccess: (baixado) => {
      notifications.show({
        color: "green",
        title: "Modelo instalado",
        message: baixado.name + " já pode ser usado.",
      });
      onEscolher(baixado.name);
      atualizar();
    },
    onError: (erro: Error) => {
      notifications.show({
        color: "red",
        title: "Não foi possível baixar",
        message: erro.message,
      });
    },
  });

  const remover = useMutation({
    mutationFn: aiApi.removerModeloLocal,
    onSuccess: atualizar,
    onError: (erro: Error) => {
      notifications.show({
        color: "red",
        title: "Não foi possível remover",
        message: erro.message,
      });
    },
  });

  if (estado.isLoading) {
    return (
      <Group gap="xs">
        <Loader size="xs" />
        <Text size="sm" c="dimmed">
          Consultando a IA local…
        </Text>
      </Group>
    );
  }

  if (!estado.data?.available) {
    return (
      <Alert color="yellow" variant="light" title="IA local fora do ar">
        Nenhum serviço respondendo nesta máquina. No aplicativo instalado ele
        sobe junto com o sistema; se você estiver rodando pelo navegador, use um
        provider de nuvem ou aponte o endereço de uma máquina que sirva a IA.
      </Alert>
    );
  }

  const instalados = estado.data.installed;
  const jaInstalado = (nome: string) =>
    instalados.some((m) => m.name === nome || m.name.startsWith(nome + ":"));

  return (
    <Stack gap="sm">
      <Text size="sm" fw={600}>
        Modelos nesta máquina
      </Text>

      {instalados.length === 0 ? (
        <Text size="sm" c="dimmed">
          Nenhum modelo baixado ainda. Escolha um da lista abaixo.
        </Text>
      ) : (
        instalados.map((m) => (
          <Paper key={m.name} p="sm" withBorder radius="md">
            <Group justify="space-between" wrap="nowrap">
              <Group gap="xs" wrap="nowrap">
                <Text size="sm" fw={m.emUso ? 700 : 400}>
                  {m.name}
                </Text>
                {m.emUso && <Badge size="sm">em uso</Badge>}
                <Text size="xs" c="dimmed">
                  {(m.size / 1024 ** 3).toFixed(1)} GB
                </Text>
              </Group>
              <Group gap="xs" wrap="nowrap">
                {!m.emUso && modelo !== m.name && (
                  <Button
                    size="xs"
                    variant="light"
                    onClick={() => onEscolher(m.name)}
                  >
                    Usar este
                  </Button>
                )}
                <Button
                  size="xs"
                  variant="subtle"
                  color="red"
                  loading={remover.isPending && remover.variables === m.name}
                  onClick={() => remover.mutate(m.name)}
                >
                  Remover
                </Button>
              </Group>
            </Group>
          </Paper>
        ))
      )}

      <Text size="sm" fw={600} mt="xs">
        Baixar outro modelo
      </Text>
      {estado.data.suggested.map((s) => (
        <Paper key={s.name} p="sm" withBorder radius="md">
          <Group justify="space-between" align="flex-start" wrap="nowrap">
            <div>
              <Group gap="xs">
                <Text size="sm" fw={600}>
                  {s.name}
                </Text>
                <Badge size="sm" variant="light">
                  {s.label}
                </Badge>
                <Text size="xs" c="dimmed">
                  {s.gigabytes.toFixed(1)} GB
                </Text>
              </Group>
              <Text size="xs" c="dimmed">
                {s.description}
              </Text>
            </div>
            <Button
              size="xs"
              variant="default"
              disabled={jaInstalado(s.name) || baixar.isPending}
              loading={baixar.isPending && baixar.variables === s.name}
              onClick={() => baixar.mutate(s.name)}
            >
              {jaInstalado(s.name) ? "Instalado" : "Baixar"}
            </Button>
          </Group>
        </Paper>
      ))}

      {baixar.isPending && (
        <Text size="xs" c="dimmed">
          Baixando {baixar.variables} — são vários gigabytes e leva alguns
          minutos. Pode deixar esta janela aberta.
        </Text>
      )}
    </Stack>
  );
}

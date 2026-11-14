import { useEffect, useRef, useState } from "react";
import { Badge, Button, Group, Paper, Stack, Text } from "@mantine/core";
import { notifications } from "@mantine/notifications";
import { runManagerApi } from "../../services/api/runManagerApi";
import type { RunExecution } from "../../types/runmanager.types";

/**
 * Terminal da execução.
 *
 * Busca o log a cada segundo mandando o tamanho que já tem, e recebe só o que
 * apareceu desde então — um teste longo passa de megabytes, e rebuscar tudo a
 * cada segundo travaria a tela.
 *
 * O aviso de estado é guardado numa referência, e não lido direto do array de
 * dependências: quem usa este componente costuma passar uma função escrita na
 * hora, que muda de identidade a cada render do pai. Como dependência, ela
 * reiniciaria a leitura sem parar, e o texto já recebido seria descartado a
 * cada vez.
 *
 * A rolagem acompanha o fim enquanto ninguém mexeu na barra; quem rolou para
 * cima está lendo alguma coisa, e puxar a tela de volta seria tirá-la da mão da
 * pessoa.
 */
export function RunTerminal({
  execucao,
  onEstado,
}: {
  execucao: RunExecution;
  onEstado?: (estado: string) => void;
}) {
  const [texto, setTexto] = useState("");
  const [estado, setEstado] = useState(execucao.state);
  const [interrompendo, setInterrompendo] = useState(false);
  const offset = useRef(0);
  const caixa = useRef<HTMLDivElement>(null);
  const noFim = useRef(true);
  const avisar = useRef(onEstado);
  avisar.current = onEstado;

  useEffect(() => {
    let vivo = true;
    let timer: number | undefined;
    offset.current = 0;
    setTexto("");
    setEstado(execucao.state);

    const buscar = async () => {
      try {
        const pedaco = await runManagerApi.log(execucao.id, offset.current);
        if (!vivo) return;
        if (pedaco.chunk) {
          offset.current = pedaco.offset;
          setTexto((atual) => atual + pedaco.chunk);
        }
        setEstado(pedaco.state as RunExecution["state"]);
        avisar.current?.(pedaco.state);
        if (pedaco.running) timer = window.setTimeout(buscar, 1000);
      } catch {
        // Falha pontual de rede não pode matar o acompanhamento: tenta de novo
        // um pouco mais devagar.
        if (vivo) timer = window.setTimeout(buscar, 3000);
      }
    };
    void buscar();

    return () => {
      vivo = false;
      if (timer) window.clearTimeout(timer);
    };
  }, [execucao.id]);

  useEffect(() => {
    if (noFim.current && caixa.current) {
      caixa.current.scrollTop = caixa.current.scrollHeight;
    }
  }, [texto]);

  const rodando = estado === "RUNNING";
  const cor =
    estado === "SUCCESS"
      ? "teal"
      : estado === "FAILED"
        ? "red"
        : estado === "CANCELLED"
          ? "gray"
          : "blue";

  const interromper = async () => {
    setInterrompendo(true);
    try {
      await runManagerApi.cancel(execucao.id);
      notifications.show({ color: "yellow", message: "Execução interrompida." });
    } catch (erro) {
      notifications.show({
        color: "red",
        title: "Não foi possível interromper",
        message: (erro as Error).message,
      });
    } finally {
      setInterrompendo(false);
    }
  };

  return (
    <Paper p="md" className="surface">
      <Stack gap="sm">
        <Group justify="space-between" wrap="wrap">
          <Group gap="xs">
            <Text size="sm" fw={700} tt="uppercase">
              Terminal da execução
            </Text>
            <Text size="xs" c="dimmed">
              #{execucao.id} · {execucao.title}
            </Text>
            <Badge size="sm" color={cor} variant={rodando ? "filled" : "light"}>
              {rodando ? "em execução" : estado}
            </Badge>
          </Group>
          <Group gap="xs">
            {/*
              O interromper fica ao lado do estado, e não escondido num menu:
              quem disparou um teste de vinte minutos no ambiente errado precisa
              achar esse botão em um segundo.
            */}
            {rodando && (
              <Button
                size="xs"
                color="red"
                loading={interrompendo}
                onClick={() => void interromper()}
              >
                Interromper execução
              </Button>
            )}
            <Button
              size="xs"
              variant="default"
              onClick={() => void navigator.clipboard?.writeText(texto)}
            >
              Copiar log
            </Button>
          </Group>
        </Group>

        <div
          ref={caixa}
          className="run-terminal"
          onScroll={(e) => {
            const alvo = e.currentTarget;
            noFim.current =
              alvo.scrollHeight - alvo.scrollTop - alvo.clientHeight < 40;
          }}
        >
          {texto || (
            <span className="run-terminal-empty">
              Esperando a primeira saída do processo…
            </span>
          )}
        </div>
      </Stack>
    </Paper>
  );
}

import { useState } from "react";
import {
  Badge,
  Button,
  Group,
  Paper,
  Stack,
  Text,
  Textarea,
} from "@mantine/core";
import { useMutation } from "@tanstack/react-query";
import { docCardApi } from "../../services/api/docCardApi";
import type { DocChatAnswer } from "../../types/docCard.types";

/**
 * Pergunta ao que o time já escreveu.
 *
 * A resposta vem sempre acompanhada das fontes, e o rótulo diz se ela saiu da
 * documentação ou dos casos de teste. Sem isso a pessoa não teria como separar
 * uma resposta ancorada em material revisado de outra montada a partir de casos
 * soltos — e as duas merecem confiança diferente.
 */
export function DocChat() {
  const [pergunta, setPergunta] = useState("");
  const [resposta, setResposta] = useState<DocChatAnswer | null>(null);

  const perguntar = useMutation({
    mutationFn: () => docCardApi.ask(pergunta),
    onSuccess: setResposta,
  });

  const enviar = () => {
    if (pergunta.trim()) perguntar.mutate();
  };

  return (
    <Paper p="lg" className="surface">
      <Stack gap="sm">
        <Text fw={700} size="sm">
          Perguntar à documentação
        </Text>
        <Group align="flex-end" gap="sm" wrap="nowrap">
          <Textarea
            style={{ flex: 1 }}
            autosize
            minRows={1}
            maxRows={4}
            placeholder="Ex.: como emitir uma nota de crédito sem ICMS?"
            value={pergunta}
            onChange={(e) => setPergunta(e.currentTarget.value)}
            onKeyDown={(e) => {
              // Enter envia; Shift+Enter continua na linha de baixo.
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                enviar();
              }
            }}
          />
          <Button
            loading={perguntar.isPending}
            disabled={!pergunta.trim()}
            onClick={enviar}
          >
            Perguntar
          </Button>
        </Group>

        {perguntar.isError && (
          <Text size="sm" c="red">
            {(perguntar.error as Error).message}
          </Text>
        )}

        {resposta && (
          <Paper p="md" withBorder radius="md">
            <Stack gap="sm">
              <Group gap="xs">
                <Badge
                  variant="light"
                  color={
                    resposta.origem === "documentacao"
                      ? "teal"
                      : resposta.origem === "suites"
                        ? "blue"
                        : "gray"
                  }
                >
                  {resposta.origem === "documentacao"
                    ? "Da documentação"
                    : resposta.origem === "suites"
                      ? "Dos casos de teste"
                      : "Sem fonte"}
                </Badge>
                {resposta.origem === "suites" && (
                  <Text size="xs" c="dimmed">
                    A documentação não cobria — isto veio das suítes, que são
                    material mais cru.
                  </Text>
                )}
              </Group>

              <Text size="sm" style={{ whiteSpace: "pre-wrap" }}>
                {resposta.answer}
              </Text>

              {resposta.sources.length > 0 && (
                <Group gap={6} wrap="wrap">
                  <Text size="xs" c="dimmed">
                    Fontes:
                  </Text>
                  {resposta.sources.map((fonte) => (
                    <Badge
                      key={fonte.tipo + fonte.id}
                      size="sm"
                      variant="outline"
                      color={fonte.tipo === "card" ? "teal" : "blue"}
                    >
                      {fonte.titulo}
                    </Badge>
                  ))}
                </Group>
              )}
            </Stack>
          </Paper>
        )}
      </Stack>
    </Paper>
  );
}

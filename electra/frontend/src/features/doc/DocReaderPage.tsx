import {
  Badge,
  Box,
  Button,
  Group,
  Paper,
  Stack,
  Text,
  ThemeIcon,
  Title,
} from "@mantine/core";
import { useQuery } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import { docCardApi } from "../../services/api/docCardApi";
import { DocContent } from "./DocContent";

/**
 * Leitura de um card.
 *
 * Tem largura de leitura e não de formulário: documentação existe para ser lida
 * inteira, e linha larga demais faz o olho se perder ao voltar para a esquerda.
 * O cabeçalho repete o ícone e o módulo para quem chegou por link direto saber
 * onde está sem precisar voltar à listagem.
 */
import { ExportarDoc } from "./ExportarDoc";

export function DocReaderPage() {
  const { id } = useParams();
  const navegar = useNavigate();

  const card = useQuery({
    queryKey: ["doc-card", id],
    queryFn: () => docCardApi.get(Number(id)),
    enabled: !!id,
  });

  if (card.isLoading) {
    return (
      <Text size="sm" c="dimmed">
        Carregando…
      </Text>
    );
  }

  if (card.isError || !card.data) {
    return (
      <Stack gap="md" align="flex-start">
        <Text size="sm" c="red">
          Não foi possível abrir este card.
        </Text>
        <Button variant="subtle" onClick={() => navegar("/doc")}>
          ← Voltar para a documentação
        </Button>
      </Stack>
    );
  }

  const doc = card.data;

  return (
    <Stack gap="lg">
      <Group gap="sm" justify="space-between" wrap="wrap">
        <Button variant="subtle" size="sm" onClick={() => navegar("/doc")}>
          ← Documentação
        </Button>
        {/* Levar o documento para fora do sistema — o Word vai para requisitos. */}
        <ExportarDoc id={doc.id} />
      </Group>

      <Paper
        p={{ base: "lg", md: "xl" }}
        radius="lg"
        className="surface"
        style={{ maxWidth: 860, width: "100%", margin: "0 auto" }}
      >
        <Stack gap="lg">
          <Group gap="md" align="flex-start" wrap="nowrap">
            <ThemeIcon
              size={54}
              radius={18}
              variant="gradient"
              gradient={{ from: "violet", to: "blue", deg: 135 }}
            >
              <span style={{ fontSize: 22 }}>◈</span>
            </ThemeIcon>
            <Box style={{ flex: 1 }}>
              <Title order={2}>{doc.title}</Title>
              {doc.description && (
                <Text size="sm" c="dimmed" mt={4}>
                  {doc.description}
                </Text>
              )}
              <Group gap="xs" mt="sm">
                {doc.systemModuleName && (
                  <Badge variant="light">{doc.systemModuleName}</Badge>
                )}
                {doc.data && (
                  <Text size="xs" c="dimmed">
                    {doc.data}
                  </Text>
                )}
                {doc.createdBy && (
                  <Text size="xs" c="dimmed">
                    · {doc.createdBy}
                  </Text>
                )}
              </Group>
            </Box>
          </Group>

          <DocContent html={doc.content ?? ""} />
        </Stack>
      </Paper>
    </Stack>
  );
}

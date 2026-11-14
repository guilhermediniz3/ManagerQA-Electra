import { useState } from "react";
import {
  Button,
  Code,
  Group,
  Loader,
  Modal,
  ScrollArea,
  Stack,
  Text,
  TextInput,
} from "@mantine/core";
import { useMutation } from "@tanstack/react-query";
import { notifications } from "@mantine/notifications";
import { docCardApi, suiteDocApi } from "../../services/api/docCardApi";
import { gherkinApi } from "../../services/api/gherkinApi";
import { useQueryClient } from "@tanstack/react-query";
import type { DocCard, SuiteDoc } from "../../types/docCard.types";
import type { GherkinFeature } from "../../types/gherkin.types";
import type { TestSuite } from "../../types/testSuite.types";
import { DocContent } from "../doc/DocContent";

type Modo = "exato" | "ia" | "gherkin";

const ROTULOS: Record<Modo, { botao: string; titulo: string; ajuda: string }> = {
  exato: {
    botao: "Criar doc",
    titulo: "Documentação da suíte",
    ajuda:
      "O conteúdo exato dos casos, sem passar por IA — inclusive os endereços de gravação.",
  },
  ia: {
    botao: "Criar doc com IA",
    titulo: "Documentação escrita pela IA",
    ajuda:
      "Material didático a partir dos casos, com as gravações no meio do passo que elas demonstram.",
  },
  gherkin: {
    botao: "Converter para Gherkin",
    titulo: "Casos em Gherkin",
    ajuda:
      "Os mesmos casos em Funcionalidade/Cenário, na ordem. Salvar guarda na aba Gherkin do plano.",
  },
};

/**
 * Gera documentação a partir da suíte aberta.
 *
 * O resultado não é salvo direto: aparece para conferência antes de virar card
 * ou arquivo. Documentação que nasce de geração automática precisa passar pelo
 * olho de quem executou os testes, senão o acervo enche de material que ninguém
 * leu.
 */
export function SuiteDocActions({ suite }: { suite: TestSuite }) {
  const [modo, setModo] = useState<Modo | null>(null);
  const [doc, setDoc] = useState<SuiteDoc | null>(null);
  const [titulo, setTitulo] = useState("");

  const gerar = useMutation({
    mutationFn: async (escolhido: Modo) => {
      const gerador =
        escolhido === "exato"
          ? suiteDocApi.exact
          : escolhido === "ia"
            ? suiteDocApi.withAi
            : suiteDocApi.gherkin;
      return gerador(suite.id);
    },
    onSuccess: (resultado, escolhido) => {
      setDoc(resultado);
      setTitulo(
        escolhido === "gherkin"
          ? resultado.title + " — Gherkin"
          : resultado.title,
      );
      setModo(escolhido);
    },
    onError: (erro: Error) => {
      notifications.show({
        color: "red",
        title: "Não foi possível gerar",
        message: erro.message,
      });
    },
  });

  const queryClient = useQueryClient();

  const salvar = useMutation<DocCard | GherkinFeature>({
    mutationFn: () => {
      // Gherkin não é documentação: vai para a aba do plano, onde fica junto das
      // suítes e pode ser editado e baixado como arquivo.
      if (modo === "gherkin") {
        return gherkinApi.save({
          name: titulo,
          content: doc!.content,
          testPlanId: suite.testPlanId,
          suiteId: suite.id,
        });
      }
      return docCardApi.save({
        title: titulo,
        description:
          "Gerado a partir da suíte " +
          suite.codeSuite +
          (modo === "ia" ? " com IA" : ""),
        content: doc!.html,
        icon: "FaRegFileAlt",
        active: true,
        suiteId: suite.id,
      });
    },
    onSuccess: () => {
      const gherkin = modo === "gherkin";
      notifications.show({
        color: "green",
        title: gherkin ? "Gherkin salvo" : "Card criado",
        message: gherkin
          ? "Está na aba Gherkin do plano, pronto para editar e baixar."
          : "A documentação está em Doc, pronta para revisar e editar.",
      });
      if (gherkin) {
        void queryClient.invalidateQueries({ queryKey: ["gherkin"] });
      }
      fechar();
    },
  });

  const fechar = () => {
    setModo(null);
    setDoc(null);
    setTitulo("");
  };

  const baixar = () => {
    if (!doc) return;
    const extensao = doc.format === "gherkin" ? "feature" : "md";
    const nome =
      titulo.replace(/[^\p{L}\p{N}]+/gu, "-").replace(/^-|-$/g, "").toLowerCase() ||
      "documentacao";
    const arquivo = new Blob([doc.content], {
      type: "text/plain;charset=utf-8",
    });
    const url = URL.createObjectURL(arquivo);
    const link = document.createElement("a");
    link.href = url;
    link.download = nome + "." + extensao;
    link.click();
    URL.revokeObjectURL(url);
  };

  const carregando = (alvo: Modo) =>
    gerar.isPending && gerar.variables === alvo;

  return (
    <>
      <Group gap="xs" wrap="wrap">
        {(Object.keys(ROTULOS) as Modo[]).map((alvo) => (
          <Button
            key={alvo}
            size="xs"
            variant="default"
            loading={carregando(alvo)}
            disabled={gerar.isPending}
            onClick={() => gerar.mutate(alvo)}
          >
            {ROTULOS[alvo].botao}
          </Button>
        ))}
      </Group>

      <Modal
        opened={!!doc}
        onClose={fechar}
        size="xl"
        title={modo ? ROTULOS[modo].titulo : ""}
      >
        {doc && modo && (
          <Stack gap="md">
            <Text size="xs" c="dimmed">
              {ROTULOS[modo].ajuda}
            </Text>

            <TextInput
              label="Título do card"
              value={titulo}
              onChange={(e) => setTitulo(e.currentTarget.value)}
            />

            <ScrollArea.Autosize mah={420} type="auto">
              {doc.format === "gherkin" ? (
                <Code block style={{ whiteSpace: "pre-wrap" }}>
                  {doc.content}
                </Code>
              ) : (
                // Prévia do mesmo HTML que vai para o editor do card.
                <DocContent html={doc.html} />
              )}
            </ScrollArea.Autosize>

            <Group justify="space-between">
              <Button variant="default" size="sm" onClick={baixar}>
                Exportar {doc.format === "gherkin" ? ".feature" : ".md"}
              </Button>
              <Group gap="sm">
                <Button variant="subtle" onClick={fechar}>
                  Fechar
                </Button>
                <Button
                  loading={salvar.isPending}
                  disabled={!titulo.trim()}
                  onClick={() => salvar.mutate()}
                >
                  {modo === "gherkin" ? "Salvar na aba Gherkin" : "Salvar em Doc"}
                </Button>
              </Group>
            </Group>
            {salvar.isError && (
              <Text size="sm" c="red">
                {(salvar.error as Error).message}
              </Text>
            )}
          </Stack>
        )}
      </Modal>

      {gerar.isPending && (
        <Group gap={6} mt={4}>
          <Loader size="xs" />
          <Text size="xs" c="dimmed">
            {gerar.variables === "exato"
              ? "Montando o conteúdo dos casos…"
              : "A IA está escrevendo. Com a IA local isso leva minutos — " +
                "deixe a tela aberta."}
          </Text>
        </Group>
      )}
    </>
  );
}

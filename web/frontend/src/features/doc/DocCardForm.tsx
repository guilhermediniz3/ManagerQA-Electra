import { useState } from "react";
import {
  Button,
  Group,
  Modal,
  Select,
  Stack,
  Switch,
  Text,
  TextInput,
  Textarea,
} from "@mantine/core";
import { useMutation, useQuery } from "@tanstack/react-query";
import { notifications } from "@mantine/notifications";
import RichTextEditor from "../../components/RichTextEditor/RichTextEditor";
import { ICONES_DISPONIVEIS } from "../../components/Card/Card";
import { docCardApi } from "../../services/api/docCardApi";
import { catalogApi } from "../../services/api/catalogApi";
import type { DocCard } from "../../types/docCard.types";

/** Uma fonte só para o que a tela oferece e o que o card sabe desenhar. */
const ICONES = ICONES_DISPONIVEIS;

/**
 * Criação e edição de um card de documentação.
 *
 * O conteúdo passa inteiro pelo editor e volta como HTML — é assim que os nós
 * próprios dele (badge, alerta, imagem e vídeo redimensionáveis) sobrevivem ao
 * salvar: eles viajam como atributos data-* dentro do próprio HTML, e qualquer
 * reescrita no caminho os transformaria em texto solto.
 */
import { ExportarDoc } from "./ExportarDoc";

export function DocCardForm({
  card,
  onClose,
  onSaved,
}: {
  card: Partial<DocCard>;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form, setForm] = useState<Partial<DocCard>>({
    icon: "FaRegFileAlt",
    active: true,
    ...card,
  });

  const modules = useQuery({
    queryKey: ["modules"],
    queryFn: () => catalogApi.list("modules"),
  });

  const salvar = useMutation({
    mutationFn: () => docCardApi.save(form),
    onSuccess: () => {
      notifications.show({ color: "green", message: "Card salvo." });
      onSaved();
    },
  });

  return (
    <Modal
      opened
      onClose={onClose}
      size="90%"
      title={form.id ? "Editar card" : "Novo card"}
    >
      <Stack gap="md">
        <Group grow align="flex-start">
          <TextInput
            label="Título"
            placeholder="Ex.: Emissão de nota de crédito"
            value={form.title ?? ""}
            onChange={(e) => setForm({ ...form, title: e.currentTarget.value })}
          />
          <Select
            label="Ícone"
            data={ICONES.map((nome) => ({ value: nome, label: nome }))}
            value={form.icon ?? "FaRegFileAlt"}
            onChange={(valor) =>
              setForm({ ...form, icon: valor ?? "FaRegFileAlt" })
            }
            searchable
          />
        </Group>

        <Textarea
          label="Descrição"
          description="Aparece no card, abaixo do título"
          autosize
          minRows={2}
          value={form.description ?? ""}
          onChange={(e) =>
            setForm({ ...form, description: e.currentTarget.value })
          }
        />

        <Group grow align="flex-end">
          <Select
            label="Módulo"
            placeholder="Sem módulo específico"
            clearable
            searchable
            data={(modules.data ?? []).map((m) => ({
              value: String(m.id),
              label: m.name,
            }))}
            value={form.systemModuleId ? String(form.systemModuleId) : null}
            onChange={(valor) =>
              setForm({
                ...form,
                systemModuleId: valor ? Number(valor) : null,
              })
            }
          />
          <Switch
            label="Ativo"
            description="Cards inativos não aparecem para o time nem alimentam a busca por IA"
            checked={form.active ?? true}
            onChange={(e) =>
              setForm({ ...form, active: e.currentTarget.checked })
            }
          />
        </Group>

        <div>
          <Text size="sm" fw={500} mb={6}>
            Conteúdo
          </Text>
          <RichTextEditor
            value={form.content ?? ""}
            onChange={(html: string) => setForm({ ...form, content: html })}
          />
        </div>

        {salvar.isError && (
          <Text size="sm" c="red">
            {(salvar.error as Error).message}
          </Text>
        )}

        <Group justify="space-between" gap="sm" wrap="wrap">
          {/*
            A exportação sai do que está gravado, e não do rascunho em tela:
            por isso o card novo, ainda sem id, tem os botões desligados.
          */}
          <ExportarDoc id={form.id} size="xs" />
          <Group gap="sm">
          <Button variant="subtle" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            loading={salvar.isPending}
            disabled={!form.title?.trim()}
            onClick={() => salvar.mutate()}
          >
            Salvar card
          </Button>
          </Group>
        </Group>
      </Stack>
    </Modal>
  );
}

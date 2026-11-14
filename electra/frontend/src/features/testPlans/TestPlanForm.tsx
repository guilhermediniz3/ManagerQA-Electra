import { useState } from "react";
import {
  Modal,
  Stack,
  TextInput,
  Textarea,
  Select,
  Button,
  SimpleGrid,
  PasswordInput,
  Switch,
} from "@mantine/core";
import { useMutation } from "@tanstack/react-query";
import type { TestPlan } from "../../types/testPlan.types";
import { testPlanApi } from "../../services/api/testPlanApi";
import { catalogApi } from "../../services/api/catalogApi";
import { useResource, useRefresh } from "../../hooks/useResource";
import { toApiDate, toInputDate } from "../../utils/dates";
export function TestPlanForm({
  initial,
  onClose,
}: {
  initial: Partial<TestPlan>;
  onClose: () => void;
}) {
  const [form, setForm] = useState(initial);
  const refresh = useRefresh();
  const developers = useResource(["developers"], () =>
      catalogApi.list("developers"),
    ),
    testers = useResource(["testers"], () => catalogApi.list("testers")),
    modules = useResource(["modules"], () => catalogApi.list("modules"));
  const save = useMutation({
    mutationFn: () => testPlanApi.save(form),
    onSuccess: () => {
      refresh();
      onClose();
    },
  });
  return (
    <Modal
      opened
      onClose={onClose}
      title={form.id ? "Editar plano" : "Novo plano local"}
      size="lg"
    >
      <form
        onSubmit={(e) => {
          e.preventDefault();
          save.mutate();
        }}
      >
        <Stack>
          <TextInput
            label="Nome do plano"
            required
            value={form.name || ""}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
          />
          <TextInput
            label="Jira Key de referência"
            required
            value={form.jira || ""}
            onChange={(e) =>
              setForm({ ...form, jira: e.target.value.toUpperCase() })
            }
          />
          <Textarea
            label="Observações"
            autosize
            minRows={3}
            value={form.observation || ""}
            onChange={(e) => setForm({ ...form, observation: e.target.value })}
          />
          <SimpleGrid cols={2}>
            <TextInput
              type="date"
              label="Data"
              required
              value={toInputDate(form.data)}
              onChange={(e) =>
                setForm({ ...form, data: toApiDate(e.target.value) })
              }
            />
            <TextInput
              type="date"
              label="Entrega"
              value={toInputDate(form.deliveryData)}
              onChange={(e) =>
                setForm({
                  ...form,
                  deliveryData: toApiDate(e.target.value) || undefined,
                })
              }
            />
          </SimpleGrid>
          <Select
            label="Developer local"
            description="Demandas sincronizadas mantêm o assignee do Jira."
            clearable
            data={(developers.data || []).map((v) => ({
              value: String(v.id),
              label: v.name,
            }))}
            value={form.developerId ? String(form.developerId) : null}
            onChange={(v) =>
              setForm({ ...form, developerId: v ? Number(v) : undefined })
            }
          />
          <Select
            label="Tester"
            clearable
            data={(testers.data || []).map((v) => ({
              value: String(v.id),
              label: v.name,
            }))}
            value={form.testerId ? String(form.testerId) : null}
            onChange={(v) =>
              setForm({ ...form, testerId: v ? Number(v) : undefined })
            }
          />
          <Select
            label="Módulo"
            clearable
            data={(modules.data || []).map((v) => ({
              value: String(v.id),
              label: v.name,
            }))}
            value={form.systemModuleId ? String(form.systemModuleId) : null}
            onChange={(v) =>
              setForm({ ...form, systemModuleId: v ? Number(v) : undefined })
            }
          />
          <TextInput
            label="Número do chamado"
            placeholder="Chamado"
            value={form.callNumber || ""}
            onChange={(e) => setForm({ ...form, callNumber: e.target.value })}
          />
          <SimpleGrid cols={2}>
            <TextInput
              label="Matriz"
              value={form.matriz || ""}
              onChange={(e) => setForm({ ...form, matriz: e.target.value })}
            />
            <TextInput
              label="Usuário de teste"
              value={form.userName || ""}
              onChange={(e) => setForm({ ...form, userName: e.target.value })}
            />
          </SimpleGrid>
          <PasswordInput
            label="Senha do ambiente de teste"
            value={form.password || ""}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
          />
          <Select
            label="Status de execução local"
            data={["EM_PROGRESSO", "CONCLUIDA", "IMPEDIMENTO", "RETORNO"]}
            value={form.status || "EM_PROGRESSO"}
            onChange={(v) => setForm({ ...form, status: v || "EM_PROGRESSO" })}
          />
          <Switch
            label="Criado pelo QA"
            checked={form.created || false}
            onChange={(e) =>
              setForm({ ...form, created: e.currentTarget.checked })
            }
          />
          <Button type="submit" loading={save.isPending}>
            Salvar plano
          </Button>
        </Stack>
      </form>
    </Modal>
  );
}

import { useState } from "react";
import {
  Modal,
  Stack,
  NumberInput,
  TextInput,
  Select,
  Button,
} from "@mantine/core";
import { useMutation } from "@tanstack/react-query";
import type { TestSuite } from "../../types/testSuite.types";
import { testSuiteApi } from "../../services/api/testSuiteApi";
import { useRefresh } from "../../hooks/useResource";
import { statusOptions } from "../../utils/status";
import { toApiDate, toInputDate } from "../../utils/dates";
export function SuiteForm({
  initial,
  onClose,
}: {
  initial: TestSuite;
  onClose: () => void;
}) {
  const [form, setForm] = useState(initial);
  const refresh = useRefresh();
  const save = useMutation({
    mutationFn: () => testSuiteApi.save(form),
    onSuccess: () => {
      refresh();
      onClose();
    },
  });
  return (
    <Modal opened onClose={onClose} title="Editar suíte">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          save.mutate();
        }}
      >
        <Stack>
          <NumberInput
            label="Código da suíte"
            required
            min={1}
            allowDecimal={false}
            value={form.codeSuite}
            onChange={(v) => setForm({ ...form, codeSuite: Number(v) })}
          />
          <TextInput
            type="date"
            label="Data"
            required
            value={toInputDate(form.data)}
            onChange={(e) =>
              setForm({ ...form, data: toApiDate(e.target.value) })
            }
          />
          <Select
            label="Situação da suíte"
            value={form.status}
            data={statusOptions}
            onChange={(v) => setForm({ ...form, status: v || "EM_PROGRESSO" })}
          />
          <Button type="submit" loading={save.isPending}>
            Salvar suíte
          </Button>
        </Stack>
      </form>
    </Modal>
  );
}

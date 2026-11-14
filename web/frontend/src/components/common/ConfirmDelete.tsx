import type { ReactNode } from "react";
import { Modal, Text, Group, Button } from "@mantine/core";
export function ConfirmDelete({
  opened,
  onClose,
  onConfirm,
  loading,
  title = "Excluir registro local",
  confirmLabel = "Excluir",
  children,
}: {
  opened: boolean;
  onClose: () => void;
  onConfirm: () => void;
  loading: boolean;
  title?: string;
  confirmLabel?: string;
  children?: ReactNode;
}) {
  return (
    <Modal opened={opened} onClose={onClose} title={title} centered>
      {children || (
        <Text>
          O registro e seus vínculos dependentes poderão ser removidos. Confirme
          a exclusão local.
        </Text>
      )}
      <Group justify="flex-end" mt="lg">
        <Button variant="default" onClick={onClose}>
          Cancelar
        </Button>
        <Button color="red" loading={loading} onClick={onConfirm}>
          {confirmLabel}
        </Button>
      </Group>
    </Modal>
  );
}

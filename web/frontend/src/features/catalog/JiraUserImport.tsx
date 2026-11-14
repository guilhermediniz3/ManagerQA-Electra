import { useState } from "react";
import { Modal, Stack, TextInput, Button, Group, Text } from "@mantine/core";
import { useMutation } from "@tanstack/react-query";
import { jiraApi } from "../../services/api/jiraApi";
import { useRefresh } from "../../hooks/useResource";
export function JiraUserImport({
  opened,
  onClose,
  kind,
}: {
  opened: boolean;
  onClose: () => void;
  kind: string;
}) {
  const [search, setSearch] = useState("");
  const refresh = useRefresh();
  const query = useMutation({ mutationFn: () => jiraApi.users(search) });
  const imp = useMutation({
    mutationFn: (id: string) => jiraApi.importUser(id, kind),
    onSuccess: () => {
      refresh();
      onClose();
    },
  });
  return (
    <Modal opened={opened} onClose={onClose} title="Pesquisar usuários do Jira">
      <Stack>
        <TextInput
          label="Nome ou e-mail"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <Button
          loading={query.isPending}
          disabled={!search.trim()}
          onClick={() => query.mutate()}
        >
          Pesquisar
        </Button>
        {query.data?.map((user) => (
          <Group justify="space-between" key={user.accountId}>
            <Text>{user.displayName}</Text>
            <Button
              size="xs"
              loading={imp.isPending}
              onClick={() => imp.mutate(user.accountId)}
            >
              Importar
            </Button>
          </Group>
        ))}
      </Stack>
    </Modal>
  );
}

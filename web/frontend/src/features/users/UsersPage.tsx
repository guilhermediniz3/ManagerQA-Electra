import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import {
  Badge,
  Button,
  Group,
  Modal,
  Paper,
  PasswordInput,
  Stack,
  Switch,
  Table,
  Text,
  TextInput,
} from "@mantine/core";
import { notifications } from "@mantine/notifications";
import { userApi } from "../../services/api/userApi";
import type { UserAccount } from "../../types/user.types";
import { useResource, useRefresh } from "../../hooks/useResource";
import { usePaged } from "../../hooks/usePaged";
import { PageHeading } from "../../components/common/PageHeading";
import { Pager } from "../../components/common/Pager";
import { ConfirmDelete } from "../../components/common/ConfirmDelete";
import { ResourceState } from "../../components/feedback/ResourceState";

export function UsersPage() {
  const q = useResource(["users"], userApi.list);
  const refresh = useRefresh();
  const [edit, setEdit] = useState<Partial<UserAccount> | null>(null);
  const [remove, setRemove] = useState<number | null>(null);

  const save = useMutation({
    mutationFn: () => userApi.save({ ...edit, password: edit?.password || undefined }),
    onSuccess: () => {
      setEdit(null);
      refresh();
    },
    onError: (erro: Error) =>
      notifications.show({ color: "red", title: "Não foi possível salvar", message: erro.message }),
  });

  const paged = usePaged(q.data || []);
  const del = useMutation({
    mutationFn: userApi.remove,
    onSuccess: () => {
      setRemove(null);
      refresh();
    },
    onError: (erro: Error) =>
      notifications.show({ color: "red", title: "Não foi possível excluir", message: erro.message }),
  });

  return (
    <>
      <PageHeading title="Usuários" subtitle="Acessos locais ao Manager">
        <Button onClick={() => setEdit({ name: "", email: "", active: true })}>
          Novo usuário
        </Button>
      </PageHeading>

      <Paper p="lg" className="surface">
        <ResourceState loading={q.isLoading} error={q.error} />
        <Table highlightOnHover layout="auto">
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Nome</Table.Th>
              <Table.Th visibleFrom="sm">E-mail</Table.Th>
              <Table.Th>Situação</Table.Th>
              <Table.Th w={1}>Ações</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {paged.slice.map((u) => (
              <Table.Tr key={u.id}>
                <Table.Td className="cell-main">
                  {u.name}
                  <Text size="xs" c="dimmed" hiddenFrom="sm">
                    {u.email}
                  </Text>
                </Table.Td>
                <Table.Td visibleFrom="sm">{u.email}</Table.Td>
                <Table.Td>
                  <Badge color={u.active ? "teal" : "gray"}>{u.active ? "Ativo" : "Inativo"}</Badge>
                </Table.Td>
                <Table.Td className="cell-action">
                  <Group gap="xs" wrap="nowrap">
                    <Button size="xs" variant="subtle" onClick={() => setEdit(u)}>
                      Editar
                    </Button>
                    <Button size="xs" variant="subtle" color="red" onClick={() => setRemove(u.id)}>
                      Excluir
                    </Button>
                  </Group>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
        <Pager
          page={paged.page}
          pages={paged.pages}
          size={paged.size}
          total={paged.total}
          from={paged.from}
          to={paged.to}
          onPage={paged.setPage}
          onSize={paged.setSize}
          unit="usuários"
        />
      </Paper>

      <Modal
        opened={!!edit}
        onClose={() => setEdit(null)}
        title={edit?.id ? "Editar usuário" : "Novo usuário"}
      >
        {edit && (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              save.mutate();
            }}
          >
            <Stack>
              <TextInput
                label="Nome"
                required
                value={edit.name || ""}
                onChange={(e) => setEdit({ ...edit, name: e.target.value })}
              />
              <TextInput
                label="E-mail"
                type="email"
                required
                value={edit.email || ""}
                onChange={(e) => setEdit({ ...edit, email: e.target.value })}
              />
              <PasswordInput
                label="Senha"
                description={edit.id ? "Deixe vazio para manter a senha." : undefined}
                required={!edit.id}
                value={edit.password || ""}
                onChange={(e) => setEdit({ ...edit, password: e.target.value })}
              />
              <Switch
                label="Ativo"
                checked={edit.active}
                onChange={(e) => setEdit({ ...edit, active: e.currentTarget.checked })}
              />
              <Button type="submit" loading={save.isPending}>
                Salvar usuário
              </Button>
            </Stack>
          </form>
        )}
      </Modal>

      <ConfirmDelete
        opened={remove !== null}
        onClose={() => setRemove(null)}
        loading={del.isPending}
        onConfirm={() => remove !== null && del.mutate(remove)}
      />
    </>
  );
}

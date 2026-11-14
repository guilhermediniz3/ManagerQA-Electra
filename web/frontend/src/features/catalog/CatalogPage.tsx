import { useState } from "react";
import {
  Badge,
  Button,
  Group,
  Modal,
  Paper,
  Stack,
  Switch,
  Table,
  TextInput,
  MultiSelect,
} from "@mantine/core";
import { useMutation } from "@tanstack/react-query";
import { catalogApi } from "../../services/api/catalogApi";
import { jiraApi } from "../../services/api/jiraApi";
import { useResource, useRefresh } from "../../hooks/useResource";
import { usePaged } from "../../hooks/usePaged";
import type { CatalogItem, CatalogKind } from "../../types/catalog.types";
import { PageHeading } from "../../components/common/PageHeading";
import { Pager } from "../../components/common/Pager";
import { ResourceState } from "../../components/feedback/ResourceState";
import { ConfirmDelete } from "../../components/common/ConfirmDelete";
import { JiraUserImport } from "./JiraUserImport";
export function CatalogPage({
  kind,
  title,
}: {
  kind: CatalogKind;
  title: string;
}) {
  const refresh = useRefresh();
  const query = useResource([kind], () => catalogApi.list(kind));
  const tech = useResource(["technologies"], () =>
    catalogApi.list("technologies"),
  );
  const [search, setSearch] = useState(""),
    [item, setItem] = useState<Partial<CatalogItem> | null>(null),
    [remove, setRemove] = useState<number | null>(null),
    [importing, setImporting] = useState(false);
  const save = useMutation({
    mutationFn: (value: Partial<CatalogItem>) => catalogApi.save(kind, value),
    onSuccess: () => {
      setItem(null);
      refresh();
    },
  });
  const del = useMutation({
    mutationFn: (id: number) => catalogApi.remove(kind, id),
    onSuccess: () => {
      setRemove(null);
      refresh();
    },
  });
  const rows = (query.data || []).filter((x) =>
    x.name.toLowerCase().includes(search.toLowerCase()),
  );
  const paged = usePaged(rows);
  return (
    <>
      <PageHeading
        title={title}
        subtitle="Pessoas e referências para organizar o trabalho de QA"
      >
        <Group>
          {(kind === "developers" || kind === "testers") && (
            <Button variant="default" onClick={() => setImporting(true)}>
              Importar do Jira
            </Button>
          )}
          <Button
            onClick={() =>
              setItem({ name: "", active: true, technologyIds: [] })
            }
          >
            Novo cadastro
          </Button>
        </Group>
      </PageHeading>
      <Paper p="lg" className="surface">
        <TextInput
          placeholder="Pesquisar por nome"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          mb="lg"
        />
        <ResourceState
          loading={query.isLoading}
          error={query.error}
          empty={!rows.length}
          retry={() => query.refetch()}
        />
        <Table striped highlightOnHover layout="auto">
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Nome</Table.Th>
              <Table.Th>Situação</Table.Th>
              <Table.Th w={1}>Ações</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {paged.slice.map((row) => (
              <Table.Tr key={row.id}>
                <Table.Td>{row.name}</Table.Td>
                <Table.Td>
                  <Badge color={row.active ? "teal" : "gray"}>
                    {row.active ? "Ativo" : "Inativo"}
                  </Badge>
                </Table.Td>
                <Table.Td className="cell-action">
                  <Group gap="xs" wrap="nowrap">
                    <Button
                      size="xs"
                      variant="subtle"
                      onClick={() => setItem(row)}
                    >
                      Editar
                    </Button>
                    <Button
                      size="xs"
                      variant="subtle"
                      color="red"
                      onClick={() => setRemove(row.id)}
                    >
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
          unit="cadastros"
        />
      </Paper>
      <Modal
        opened={!!item}
        onClose={() => setItem(null)}
        title={item?.id ? "Editar cadastro" : "Novo cadastro"}
      >
        {item && (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              save.mutate(item);
            }}
          >
            <Stack>
              <TextInput
                label="Nome"
                required
                value={item.name}
                onChange={(e) => setItem({ ...item, name: e.target.value })}
              />
              {kind === "developers" && (
                <MultiSelect
                  label="Tecnologias"
                  data={(tech.data || []).map((t) => ({
                    value: String(t.id),
                    label: t.name,
                  }))}
                  value={(item.technologyIds || []).map(String)}
                  onChange={(v) =>
                    setItem({ ...item, technologyIds: v.map(Number) })
                  }
                />
              )}
              <Switch
                label="Ativo"
                checked={item.active}
                onChange={(e) =>
                  setItem({ ...item, active: e.currentTarget.checked })
                }
              />
              <Button type="submit" loading={save.isPending}>
                Salvar
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
      <JiraUserImport
        opened={importing}
        onClose={() => setImporting(false)}
        kind={kind === "developers" ? "developer" : "tester"}
      />
    </>
  );
}

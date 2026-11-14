import { Group, Pagination, Select, Text } from "@mantine/core";
import { PAGE_SIZES } from "../../hooks/usePaged";
export function Pager({
  page,
  pages,
  size,
  total,
  from,
  to,
  onPage,
  onSize,
  unit = "registros",
}: {
  page: number;
  pages: number;
  size: number;
  total: number;
  from: number;
  to: number;
  onPage: (page: number) => void;
  onSize: (size: number) => void;
  unit?: string;
}) {
  if (!total) return null;
  return (
    <Group justify="space-between" gap="md" wrap="wrap" mt="xs">
      <Group gap="xs" wrap="nowrap">
        <Text size="sm" c="dimmed" style={{ whiteSpace: "nowrap" }}>
          {from}–{to} de {total} {unit}
        </Text>
        <Select
          size="xs"
          w={92}
          aria-label={"Itens por página (" + unit + ")"}
          data={PAGE_SIZES.map((value) => ({
            value: String(value),
            label: value + " / pág.",
          }))}
          value={String(size)}
          onChange={(value) => value && onSize(Number(value))}
          allowDeselect={false}
          comboboxProps={{ withinPortal: true }}
        />
      </Group>
      <Pagination
        total={pages}
        value={page}
        onChange={onPage}
        size="sm"
        radius="md"
        withEdges={pages > 5}
        siblings={1}
        boundaries={1}
      />
    </Group>
  );
}

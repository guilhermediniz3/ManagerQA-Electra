import { SimpleGrid, Select, TextInput } from "@mantine/core";
import type { Demand } from "../../types/demand.types";
export interface Filters {
  search: string;
  squad: string | null;
  sprint: string | null;
  tester: string | null;
  status: string | null;
}
export const initialFilters: Filters = {
  search: "",
  squad: null,
  sprint: null,
  tester: null,
  status: null,
};
export function filterDemands(rows: Demand[], f: Filters) {
  return rows.filter(
    (d) =>
      (!f.search ||
        (d.jiraKey + " " + d.summary)
          .toLowerCase()
          .includes(f.search.toLowerCase())) &&
      (!f.squad || d.squad === f.squad) &&
      (!f.sprint || d.sprint === f.sprint) &&
      (!f.tester || d.testerName === f.tester) &&
      (!f.status || d.statusId === f.status),
  );
}
export function DemandFilters({
  rows,
  value,
  onChange,
  statuses,
}: {
  rows: Demand[];
  value: Filters;
  onChange: (f: Filters) => void;
  statuses: { value: string; label: string }[];
}) {
  const options = (key: "squad" | "sprint" | "testerName") => [
    ...new Set(rows.map((d) => d[key]).filter((v): v is string => !!v)),
  ];
  return (
    <SimpleGrid cols={{ base: 1, sm: 2, lg: 5 }} mb="lg">
      <TextInput
        placeholder="Jira Key ou título"
        value={value.search}
        onChange={(e) => onChange({ ...value, search: e.target.value })}
      />
      <Select
        placeholder="Squad"
        clearable
        searchable
        data={options("squad")}
        value={value.squad}
        onChange={(v) => onChange({ ...value, squad: v })}
      />
      <Select
        placeholder="Sprint"
        clearable
        searchable
        data={options("sprint")}
        value={value.sprint}
        onChange={(v) => onChange({ ...value, sprint: v })}
      />
      <Select
        placeholder="Tester"
        clearable
        searchable
        data={options("testerName")}
        value={value.tester}
        onChange={(v) => onChange({ ...value, tester: v })}
      />
      <Select
        placeholder="Status Jira"
        clearable
        searchable
        data={statuses}
        value={value.status}
        onChange={(v) => onChange({ ...value, status: v })}
      />
    </SimpleGrid>
  );
}

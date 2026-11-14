import type { JiraNamed } from "../../types/jira.types";

export interface StatusGroup {
  value: string;
  label: string;
  ids: string[];
}

export function groupJiraStatuses(statuses: JiraNamed[]): StatusGroup[] {
  const groups = new Map<string, StatusGroup>();
  for (const status of statuses) {
    const label = status.name.normalize("NFC").trim().replace(/\s+/g, " ");
    const value = label.toLocaleLowerCase("pt-BR");
    const group = groups.get(value) || { value, label, ids: [] };
    const id = String(status.id);
    if (!group.ids.includes(id)) group.ids.push(id);
    groups.set(value, group);
  }
  return [...groups.values()].sort((a, b) =>
    a.label.localeCompare(b.label, "pt-BR"),
  );
}

export function resolveStatusIds(
  groups: StatusGroup[],
  selected: string[],
): string[] {
  const ids = new Set<string>();
  for (const name of selected) {
    const group = groups.find((group) => group.value === name);
    if (!group)
      throw new Error(
        "Os status foram atualizados. Selecione os status novamente.",
      );
    group.ids.forEach((id) => ids.add(id));
  }
  return [...ids];
}

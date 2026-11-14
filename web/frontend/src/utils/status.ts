import type { CaseStatus } from "../types/testCase.types";
export const STATUS_ORDER: CaseStatus[] = [
  "RETORNO",
  "IMPEDIMENTO",
  "EM_PROGRESSO",
  "CONCLUIDA",
];
export const statusMeta: Record<
  CaseStatus,
  { label: string; short: string; color: string }
> = {
  RETORNO: { label: "Falhou", short: "Falhou", color: "red" },
  IMPEDIMENTO: { label: "Impedimento", short: "Impedimento", color: "#946F00" },
  EM_PROGRESSO: { label: "Em progresso", short: "Em progresso", color: "blue" },
  CONCLUIDA: { label: "Aprovado", short: "Aprovado", color: "green" },
};
export const statusOptions = STATUS_ORDER.map((value) => ({
  value,
  label: statusMeta[value].label,
}));
export const statusLabel = (status?: string) =>
  statusMeta[status as CaseStatus]?.label || status || "—";
export const statusColor = (status?: string) =>
  statusMeta[status as CaseStatus]?.color || "gray";
export interface StatusTally {
  total: number;
  counts: Record<CaseStatus, number>;
  percent: Record<CaseStatus, number>;
  done: number;
  donePercent: number;
}
export function tally(items: { status?: string }[]): StatusTally {
  const counts = { RETORNO: 0, IMPEDIMENTO: 0, EM_PROGRESSO: 0, CONCLUIDA: 0 };
  for (const item of items)
    if (item.status && item.status in counts)
      counts[item.status as CaseStatus]++;
  const total = items.length;
  const pct = (n: number) => (total ? Math.round((n / total) * 100) : 0);
  return {
    total,
    counts,
    percent: {
      RETORNO: pct(counts.RETORNO),
      IMPEDIMENTO: pct(counts.IMPEDIMENTO),
      EM_PROGRESSO: pct(counts.EM_PROGRESSO),
      CONCLUIDA: pct(counts.CONCLUIDA),
    },
    done: counts.CONCLUIDA,
    donePercent: pct(counts.CONCLUIDA),
  };
}

import { Group, Progress, Stack, Text, Tooltip } from "@mantine/core";
import { STATUS_ORDER, statusMeta, type StatusTally } from "../../utils/status";
export function StatusProgress({
  tally: t,
  size = "md",
  legend = true,
}: {
  tally: StatusTally;
  size?: string;
  legend?: boolean;
}) {
  const visible = STATUS_ORDER.filter((s) => t.counts[s] > 0);
  return (
    <Stack gap={8}>
      <Progress.Root size={size} radius="xl" className="status-bar">
        {visible.map((s) => (
          <Tooltip
            key={s}
            withArrow
            label={
              statusMeta[s].label +
              ": " +
              t.counts[s] +
              " CT · " +
              t.percent[s] +
              "%"
            }
          >
            <Progress.Section
              value={t.percent[s]}
              color={statusMeta[s].color}
            />
          </Tooltip>
        ))}
      </Progress.Root>
      {legend && (
        <Group gap="lg" wrap="wrap">
          {STATUS_ORDER.map((s) => (
            <Group key={s} gap={7} wrap="nowrap">
              <span
                className={"status-dot status-dot-" + statusMeta[s].color}
                style={{background: `var(--mantine-color-${statusMeta[s].color}-6)`}}
              />
              <Text size="xs" c={t.counts[s] ? undefined : "dimmed"}>
                {statusMeta[s].short}{" "}
                <Text span fw={700}>
                  {t.percent[s]}%
                </Text>
                <Text span c="dimmed">
                  {" "}
                  ({t.counts[s]})
                </Text>
              </Text>
            </Group>
          ))}
        </Group>
      )}
    </Stack>
  );
}

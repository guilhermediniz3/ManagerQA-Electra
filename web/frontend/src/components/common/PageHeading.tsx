import { Group, Stack, Text, Title } from "@mantine/core";
import type { ReactNode } from "react";
export function PageHeading({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle: string;
  children?: ReactNode;
}) {
  return (
    <Group
      justify="space-between"
      align="flex-end"
      mb="xl"
      wrap="wrap"
      gap="md"
    >
      <Stack gap={6} style={{ minWidth: 0, flex: 1 }}>
        <Title order={2} className="page-title" fz={{ base: 24, sm: 30 }}>
          {title}
        </Title>
        <Text c="dimmed" size="sm">
          {subtitle}
        </Text>
      </Stack>
      {children}
    </Group>
  );
}

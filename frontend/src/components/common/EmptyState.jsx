import { Paper, Stack, Text, Title } from "@mantine/core";

/** Shown when a list has no items; `action` is usually the button that creates the first one. */
export function EmptyState({ title, message, action }) {
  return (
    <Paper withBorder p="xl">
      <Stack gap="xs" align="center" ta="center">
        <Title order={2} size="h4">
          {title}
        </Title>
        {message && <Text c="dimmed">{message}</Text>}
        {action}
      </Stack>
    </Paper>
  );
}

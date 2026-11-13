import { Group, Progress, Stack, Text } from "@mantine/core";

/** "Pallets 32 / 33 (97%)" with a progress bar; `format` renders the amounts. */
export function UtilisationBar({ label, used, capacity, percent, format }) {
  return (
    <Stack gap={4}>
      <Group justify="space-between" gap="xs" wrap="nowrap">
        <Text size="sm" c="dimmed">
          {label}
        </Text>
        <Text size="sm" className="hw-numeric">
          {format(used)} / {format(capacity)} ({percent}%)
        </Text>
      </Group>
      <Progress
        value={Math.min(percent, 100)}
        aria-label={`${label} used: ${percent}%`}
        size="md"
      />
    </Stack>
  );
}

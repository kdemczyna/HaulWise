import { Badge, Group, Paper, Stack, Text, Title } from "@mantine/core";
import {
  formatDuration,
  formatKm,
  formatLitres,
  formatPln,
  formatSigned,
} from "../../utils/format.js";
import { nothingToCompare } from "../../utils/plan.js";

/**
 * The cost of priority (US-6): the extra PLN of the priority plan, the delta sentence as
 * delivered by the backend and the delta chips. Variants for "no extra cost" and
 * "nothing to compare" (no shipment loaded).
 */
export function DeltaBanner({ plan }) {
  const { delta, optimal, priority } = plan;
  const empty = nothingToCompare(plan);
  const tone = empty ? "neutral" : delta.noExtraCost ? "success" : "priority";

  return (
    <Paper
      withBorder
      p="lg"
      className="hw-delta-banner"
      data-tone={tone}
      component="section"
      aria-labelledby="delta-banner-title"
    >
      <Stack gap="sm">
        <Title order={2} size="h5" id="delta-banner-title" c="dimmed">
          Cost of priority
        </Title>
        {tone === "priority" && (
          <Text className="hw-delta-amount">
            {formatSigned(delta.costPln, formatPln)}
          </Text>
        )}
        {tone === "success" && (
          <Text className="hw-delta-amount">No extra cost</Text>
        )}
        <Text size="lg" fw={500}>
          {delta.sentence}
        </Text>
        {!empty && (
          <Group gap="xs" wrap="wrap">
            <Badge variant="default" radius="xl" tt="none">
              {formatSigned(delta.distanceKm, formatKm)}
            </Badge>
            <Badge variant="default" radius="xl" tt="none">
              {formatSigned(delta.durationMinutes, formatDuration)}
            </Badge>
            <Badge variant="default" radius="xl" tt="none">
              {formatSigned(delta.litres, formatLitres)} fuel
            </Badge>
            <Text size="sm" c="dimmed">
              Deadlines missed: {optimal.deadlinesMissed} →{" "}
              {priority.deadlinesMissed}
            </Text>
          </Group>
        )}
      </Stack>
    </Paper>
  );
}

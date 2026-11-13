import { Group, Stack, Text, VisuallyHidden } from "@mantine/core";
import { formatDateTime, formatDuration } from "../../utils/format.js";
import { minutesLate } from "../../utils/plan.js";
import { PinnedTag } from "../common/PinnedTag.jsx";
import { PriorityBadge } from "../common/PriorityBadge.jsx";

/** Pinned stops are forced first only by PRIORITY; OPTIMAL ignores the pin (section 5, `pinned`). */
const PINNED_LABELS = {
  optimal: "Pinned first - ignored by optimal",
  priority: "Pinned first",
};

function DeadlineStatus({ stop }) {
  if (!stop.deadline) {
    return null;
  }
  const deadline = formatDateTime(stop.deadline);
  if (stop.deadlineMissed) {
    return (
      <Text size="sm" fw={600} className="hw-status-late">
        {formatDuration(minutesLate(stop))} late (deadline {deadline})
      </Text>
    );
  }
  return (
    <Text size="sm" className="hw-status-on-time">
      on time (deadline {deadline})
    </Text>
  );
}

/** Numbered stops of one route in driving order: arrival, drop, priority, pin and deadline status. */
export function StopTimeline({ stops, variant }) {
  return (
    <ol className="hw-stops">
      {stops.map((stop) => (
        <li key={stop.sequence} className="hw-stop">
          <span className="hw-stop-number" aria-hidden="true">
            {stop.sequence}
          </span>
          <Stack gap={2}>
            <Group gap="xs" wrap="wrap">
              <Text fw={600}>
                <VisuallyHidden>Stop {stop.sequence}: </VisuallyHidden>
                {stop.city}
              </Text>
              <Text size="sm" c="dimmed">
                {stop.name}
              </Text>
              <PriorityBadge priority={stop.priority} />
              {stop.pinned && <PinnedTag label={PINNED_LABELS[variant]} />}
            </Group>
            <Text size="sm" className="hw-numeric">
              Arrives {formatDateTime(stop.arrivalTime)}, drops{" "}
              {stop.dropPallets} pallets
            </Text>
            <DeadlineStatus stop={stop} />
          </Stack>
        </li>
      ))}
    </ol>
  );
}

import { Badge, useMantineTheme } from "@mantine/core";
import { PRIORITY_LABELS } from "../../utils/priority.js";

/** Pill tag for a shipment priority, coloured by the approved priority tokens (8.1). */
export function PriorityBadge({ priority }) {
  const theme = useMantineTheme();
  const tag =
    theme.other.priorityTags[priority] ?? theme.other.priorityTags.NORMAL;
  return (
    <Badge
      radius="xl"
      variant="filled"
      data-priority={priority}
      styles={{ root: { background: tag.background, color: tag.color } }}
    >
      {PRIORITY_LABELS[priority] ?? priority}
    </Badge>
  );
}

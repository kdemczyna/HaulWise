import { Badge } from "@mantine/core";

/** Outline pill shown next to the priority of a shipment that should be delivered first. */
export function PinnedTag({ label = "Pinned first" }) {
  return (
    <Badge radius="xl" variant="default" tt="none" fw={500}>
      {label}
    </Badge>
  );
}

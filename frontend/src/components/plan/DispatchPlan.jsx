import { Badge, Button, List, Text } from "@mantine/core";
import { useState } from "react";
import { dispatchPlan } from "../../api/plans.js";
import { formatDateTime } from "../../utils/format.js";
import { notifySuccess } from "../../utils/notify.js";
import { VARIANT_LABELS, dispatchScope } from "../../utils/plan.js";
import { ConfirmModal } from "../common/ConfirmModal.jsx";

function plural(count, word) {
  return `${count} ${word}${count === 1 ? "" : "s"}`;
}

/**
 * "Send trucks" for the variant shown on screen (T-33): confirms what happens, then records
 * the dispatch. Once `plan.dispatchedAt` is set it shows when the plan was sent instead, so
 * reopening the plan never offers to send it twice. `onDispatched` reloads the plan.
 */
export function DispatchPlan({ plan, variant, onDispatched }) {
  const [opened, setOpened] = useState(false);

  if (plan.dispatchedAt) {
    return (
      <Badge
        size="lg"
        radius="xl"
        variant="outline"
        color="var(--hw-color-success)"
        tt="none"
        role="status"
      >
        Sent {formatDateTime(plan.dispatchedAt)}
      </Badge>
    );
  }

  const label = VARIANT_LABELS[variant];
  const { plates, shipmentCount } = dispatchScope(plan, variant);

  const send = async () => {
    const result = await dispatchPlan(plan.id, variant.toUpperCase());
    setOpened(false);
    notifySuccess(
      `Sent ${plural(result.trucks.length, "truck")}; ${plural(result.removedShipments.length, "shipment")} removed from the list`,
    );
    onDispatched();
  };

  return (
    <>
      <Button
        color="var(--hw-color-success)"
        onClick={() => setOpened(true)}
        disabled={plates.length === 0}
      >
        Send trucks
      </Button>
      <ConfirmModal
        opened={opened}
        onClose={() => setOpened(false)}
        onConfirm={send}
        title={`Send the ${label.toLowerCase()} plan?`}
        confirmLabel="Send trucks"
        confirmColor="var(--hw-color-success)"
        errorTitle="Could not send the trucks"
      >
        <Text>
          This removes {plural(shipmentCount, "shipment")} from the shipment
          list and marks {plural(plates.length, "truck")} as on route:
        </Text>
        <List size="sm">
          {plates.map((plate) => (
            <List.Item key={plate}>{plate}</List.Item>
          ))}
        </List>
        <Text size="sm" c="dimmed">
          Unassigned shipments stay in the list. This cannot be undone; a truck
          can be marked available again on the Trucks page.
        </Text>
      </ConfirmModal>
    </>
  );
}

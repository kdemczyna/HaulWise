import { notifications } from "@mantine/notifications";

/**
 * Short confirmation after a successful change, e.g. "Truck WX 1001A deleted".
 * Announced politely (role "status"), not as an alert.
 */
export function notifySuccess(message) {
  notifications.show({ color: "green", message, role: "status" });
}

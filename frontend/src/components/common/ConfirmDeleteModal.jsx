import { Text } from "@mantine/core";
import { ConfirmModal } from "./ConfirmModal.jsx";

/**
 * Asks before deleting `itemName` (e.g. "PO-1100"). `onConfirm` returns a promise; when it
 * rejects (e.g. 409 "Warehouse 'GDN Port' is used by 3 shipments.") the message is shown
 * here and the dialog stays open. See {@link ConfirmModal}.
 */
export function ConfirmDeleteModal({
  opened,
  onClose,
  onConfirm,
  itemType,
  itemName,
}) {
  return (
    <ConfirmModal
      opened={opened}
      onClose={onClose}
      onConfirm={onConfirm}
      title={`Delete ${itemName}?`}
      confirmLabel="Delete"
      confirmColor="var(--hw-color-danger)"
      errorTitle="Could not delete"
    >
      <Text>
        The {itemType} <strong>{itemName}</strong> will be deleted. This cannot
        be undone.
      </Text>
    </ConfirmModal>
  );
}

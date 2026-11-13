import { Alert, Button, Group, Modal, Stack, Text } from "@mantine/core";
import { useState } from "react";
import { errorMessage } from "../../api/client.js";

/**
 * Asks before deleting `itemName` (e.g. "PO-1100"). `onConfirm` returns a promise; when it
 * rejects (e.g. 409 "Warehouse 'GDN Port' is used by 3 shipments.") the message is shown
 * here and the dialog stays open. Cancel has the initial focus, so Enter never deletes.
 * While the delete runs the dialog cannot be dismissed.
 */
export function ConfirmDeleteModal({
  opened,
  onClose,
  onConfirm,
  itemType,
  itemName,
}) {
  const [deleting, setDeleting] = useState(false);

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title={`Delete ${itemName}?`}
      closeOnEscape={!deleting}
      closeOnClickOutside={!deleting}
      withCloseButton={!deleting}
    >
      <ConfirmDeleteContent
        onClose={onClose}
        onConfirm={onConfirm}
        itemType={itemType}
        itemName={itemName}
        deleting={deleting}
        setDeleting={setDeleting}
      />
    </Modal>
  );
}

/** Separate component so the error starts fresh every time the modal opens. */
function ConfirmDeleteContent({
  onClose,
  onConfirm,
  itemType,
  itemName,
  deleting,
  setDeleting,
}) {
  const [error, setError] = useState(null);

  async function handleDelete() {
    setDeleting(true);
    setError(null);
    try {
      await onConfirm();
    } catch (failure) {
      setError(failure);
    } finally {
      setDeleting(false);
    }
  }

  return (
    <Stack gap="md">
      <Text>
        The {itemType} <strong>{itemName}</strong> will be deleted. This cannot
        be undone.
      </Text>
      {error && (
        <Alert color="red" variant="light" title="Could not delete">
          {errorMessage(error)}
        </Alert>
      )}
      <Group justify="flex-end" gap="sm">
        <Button
          variant="default"
          onClick={onClose}
          disabled={deleting}
          data-autofocus
        >
          Cancel
        </Button>
        <Button
          color="var(--hw-color-danger)"
          onClick={handleDelete}
          loading={deleting}
        >
          Delete
        </Button>
      </Group>
    </Stack>
  );
}

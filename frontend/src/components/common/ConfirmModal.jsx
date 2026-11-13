import { Alert, Button, Group, Modal, Stack } from "@mantine/core";
import { useState } from "react";
import { errorMessage } from "../../api/client.js";

/**
 * Asks before an action that cannot be undone. `onConfirm` returns a promise; when it
 * rejects the server message is shown under `errorTitle` and the dialog stays open.
 * Cancel has the initial focus, so Enter never confirms. While the action runs the dialog
 * cannot be dismissed.
 */
export function ConfirmModal({
  opened,
  onClose,
  onConfirm,
  title,
  children,
  confirmLabel,
  confirmColor,
  errorTitle,
}) {
  const [running, setRunning] = useState(false);

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title={title}
      closeOnEscape={!running}
      closeOnClickOutside={!running}
      withCloseButton={!running}
    >
      <ConfirmContent
        onClose={onClose}
        onConfirm={onConfirm}
        confirmLabel={confirmLabel}
        confirmColor={confirmColor}
        errorTitle={errorTitle}
        running={running}
        setRunning={setRunning}
      >
        {children}
      </ConfirmContent>
    </Modal>
  );
}

/** Separate component so the error starts fresh every time the modal opens. */
function ConfirmContent({
  onClose,
  onConfirm,
  confirmLabel,
  confirmColor,
  errorTitle,
  running,
  setRunning,
  children,
}) {
  const [error, setError] = useState(null);

  async function handleConfirm() {
    setRunning(true);
    setError(null);
    try {
      await onConfirm();
    } catch (failure) {
      setError(failure);
    } finally {
      setRunning(false);
    }
  }

  return (
    <Stack gap="md">
      {children}
      {error && (
        <Alert color="red" variant="light" title={errorTitle}>
          {errorMessage(error)}
        </Alert>
      )}
      <Group justify="flex-end" gap="sm">
        <Button
          variant="default"
          onClick={onClose}
          disabled={running}
          data-autofocus
        >
          Cancel
        </Button>
        <Button color={confirmColor} onClick={handleConfirm} loading={running}>
          {confirmLabel}
        </Button>
      </Group>
    </Stack>
  );
}

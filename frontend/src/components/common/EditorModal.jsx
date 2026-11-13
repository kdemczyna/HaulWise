import { Modal } from "@mantine/core";

/** Add / edit modal that cannot be dismissed (Escape, X, outside click) while `busy`. */
export function EditorModal({ opened, onClose, title, busy, children }) {
  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title={title}
      size="lg"
      closeOnEscape={!busy}
      closeOnClickOutside={!busy}
      withCloseButton={!busy}
    >
      {children}
    </Modal>
  );
}

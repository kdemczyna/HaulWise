import { useState } from "react";

const CLOSED = { opened: false, item: null };

/**
 * Open / close state of a list page's add-edit modal and delete confirmation, plus
 * `busy` while a save or delete runs (the dialogs cannot be closed then).
 * The item is kept while a modal closes, so its title does not change during the transition.
 */
export function useCrudDialogs() {
  const [editor, setEditor] = useState(CLOSED);
  const [deletion, setDeletion] = useState(CLOSED);
  const [busy, setBusy] = useState(false);

  /** Wraps an async handler so `busy` is true while it runs. */
  const track =
    (handler) =>
    async (...args) => {
      setBusy(true);
      try {
        return await handler(...args);
      } finally {
        setBusy(false);
      }
    };

  return {
    editor,
    openCreate: () => setEditor({ opened: true, item: null }),
    openEdit: (item) => setEditor({ opened: true, item }),
    closeEditor: () => setEditor((state) => ({ ...state, opened: false })),
    deletion,
    openDelete: (item) => setDeletion({ opened: true, item }),
    closeDelete: () => setDeletion((state) => ({ ...state, opened: false })),
    busy,
    track,
  };
}

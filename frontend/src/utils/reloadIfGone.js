/**
 * Runs `action`; when it fails with 404 (the item was deleted elsewhere) the list is
 * reloaded so it no longer shows the stale row. The error is rethrown, so the dialog
 * still shows the server message.
 */
export async function reloadIfGone(action, reload) {
  try {
    return await action();
  } catch (error) {
    if (error?.status === 404) {
      reload();
    }
    throw error;
  }
}

import { Box } from "@mantine/core";
import { ErrorState } from "./ErrorState.jsx";
import { LoadingState } from "./LoadingState.jsx";

/**
 * Loading, error (with Retry) and empty states around a list from `useAsync`.
 * `label` names the items in messages ("trucks"); `children(items)` renders a non-empty list.
 * Data of the previous load stays visible (dimmed, `aria-busy`) while a reload runs.
 */
export function ListView({ result, label, empty, children }) {
  const { data, error, loading, reload } = result;
  if (error) {
    return (
      <ErrorState
        title={`Could not load ${label}`}
        error={error}
        onRetry={reload}
      />
    );
  }
  if (loading && !data) {
    return <LoadingState label={`Loading ${label}…`} />;
  }
  return (
    <Box
      aria-busy={loading}
      className="hw-list"
      data-reloading={loading || undefined}
    >
      {!data || data.length === 0 ? empty : children(data)}
    </Box>
  );
}

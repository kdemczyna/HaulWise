import { Skeleton, Stack, VisuallyHidden } from "@mantine/core";

/** Skeleton placeholder with a screen-reader status message. */
export function LoadingState({ label = "Loading…", rows = 3 }) {
  return (
    <Stack gap="sm" role="status" aria-live="polite">
      <VisuallyHidden>{label}</VisuallyHidden>
      {Array.from({ length: rows }, (_, index) => (
        <Skeleton key={index} height={36} aria-hidden="true" />
      ))}
    </Stack>
  );
}

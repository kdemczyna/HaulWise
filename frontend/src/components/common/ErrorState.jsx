import { Alert, Button, Stack, Text } from "@mantine/core";
import { errorMessage } from "../../api/client.js";

/** Readable error message (never raw JSON) with an optional Retry button. */
export function ErrorState({ error, onRetry, title = "Could not load data" }) {
  return (
    <Alert color="red" variant="light" title={title}>
      <Stack gap="sm" align="flex-start">
        <Text size="sm">{errorMessage(error)}</Text>
        {onRetry && (
          <Button variant="default" size="xs" onClick={onRetry}>
            Retry
          </Button>
        )}
      </Stack>
    </Alert>
  );
}

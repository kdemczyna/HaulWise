import { Alert, Button, Group, Stack } from "@mantine/core";

/** Form-level error (anything that is not a field error) above Cancel / Save. */
export function FormFooter({ formError, submitting, submitLabel, onCancel }) {
  return (
    <Stack gap="md">
      {formError && (
        <Alert color="red" variant="light" role="alert">
          {formError}
        </Alert>
      )}
      <Group justify="flex-end" gap="sm" className="hw-form-footer">
        <Button variant="default" onClick={onCancel} disabled={submitting}>
          Cancel
        </Button>
        <Button type="submit" loading={submitting}>
          {submitLabel}
        </Button>
      </Group>
    </Stack>
  );
}

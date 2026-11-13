import { Button, Group, Table, VisuallyHidden } from "@mantine/core";

/** Header cell of the actions column (named for screen readers only). */
export function ActionsHeader() {
  return (
    <Table.Th>
      <VisuallyHidden>Actions</VisuallyHidden>
    </Table.Th>
  );
}

/** Edit / Delete buttons of a table row; their accessible names include `itemName`. */
export function RowActions({ itemName, onEdit, onDelete, editDisabled }) {
  return (
    <Table.Td>
      <Group gap="xs" justify="flex-end" wrap="nowrap">
        <Button
          variant="default"
          size="xs"
          onClick={onEdit}
          disabled={editDisabled}
          aria-label={`Edit ${itemName}`}
        >
          Edit
        </Button>
        <Button
          variant="default"
          size="xs"
          className="hw-danger-button"
          onClick={onDelete}
          aria-label={`Delete ${itemName}`}
        >
          Delete
        </Button>
      </Group>
    </Table.Td>
  );
}

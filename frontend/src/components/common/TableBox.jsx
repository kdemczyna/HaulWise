import { Paper, Table } from "@mantine/core";

/**
 * Bordered table that scrolls horizontally inside its box below `minWidth`, so narrow
 * screens never scroll the whole page. `label` is the table's accessible name.
 */
export function TableBox({ label, minWidth, children }) {
  return (
    <Paper withBorder className="hw-table-box">
      <Table.ScrollContainer minWidth={minWidth} type="native">
        <Table
          aria-label={label}
          verticalSpacing="sm"
          horizontalSpacing="md"
          highlightOnHover
          className="hw-table"
        >
          {children}
        </Table>
      </Table.ScrollContainer>
    </Paper>
  );
}

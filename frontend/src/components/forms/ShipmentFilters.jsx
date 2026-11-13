import {
  Fieldset,
  Group,
  NativeSelect,
  Paper,
  SegmentedControl,
} from "@mantine/core";
import { PRIORITIES, PRIORITY_LABELS } from "../../utils/priority.js";

const PRIORITY_FILTERS = [
  { value: "ALL", label: "All" },
  ...PRIORITIES.map((value) => ({ value, label: PRIORITY_LABELS[value] })),
];

/** Origin warehouse ("" = all) and priority ("ALL" or an API priority) filters of the shipments list. */
export function ShipmentFilters({
  warehouses,
  origin,
  onOriginChange,
  priority,
  onPriorityChange,
}) {
  const originOptions = [
    { value: "", label: "All warehouses" },
    ...warehouses.map((warehouse) => ({
      value: String(warehouse.id),
      label: warehouse.name,
    })),
  ];

  return (
    <Paper withBorder px="md" py="sm">
      <Group gap="md" align="flex-end" wrap="wrap">
        <NativeSelect
          label="From warehouse"
          data={originOptions}
          value={origin}
          onChange={(event) => onOriginChange(event.currentTarget.value)}
          miw={200}
        />
        <Fieldset
          legend="Priority"
          variant="unstyled"
          className="hw-filter-fieldset"
        >
          <SegmentedControl
            data={PRIORITY_FILTERS}
            value={priority}
            onChange={onPriorityChange}
          />
        </Fieldset>
      </Group>
    </Paper>
  );
}

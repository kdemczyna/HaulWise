import {
  Checkbox,
  Fieldset,
  NativeSelect,
  NumberInput,
  SegmentedControl,
  SimpleGrid,
  Stack,
  TextInput,
} from "@mantine/core";
import { useForm } from "@mantine/form";
import { useRef } from "react";
import { useFormSubmit } from "../../hooks/useFormSubmit.js";
import { fromDateTimeInput, toDateTimeInput } from "../../utils/format.js";
import { PRIORITY_LABELS } from "../../utils/priority.js";
import {
  numberRange,
  requiredChoice,
  textLength,
} from "../../utils/validation.js";
import { FormFooter } from "./FormFooter.jsx";

/** Least important first, as in the approved design. */
const PRIORITY_OPTIONS = ["NORMAL", "HIGH", "URGENT"].map((value) => ({
  value,
  label: PRIORITY_LABELS[value],
}));

const ORIGIN_DIFFERS = "Must differ from the origin warehouse";

export const INCOMPLETE_DEADLINE =
  "Enter both a date and a time, or clear the field";

function toFormValues(shipment, defaultOriginId) {
  return {
    reference: shipment?.reference ?? "",
    originId: String(shipment?.origin.id ?? defaultOriginId ?? ""),
    destinationId: String(shipment?.destination.id ?? ""),
    pallets: shipment?.pallets ?? "",
    weightKg: shipment?.weightKg ?? "",
    priority: shipment?.priority ?? "NORMAL",
    pinnedFirst: shipment?.pinnedFirst ?? false,
    deadline: toDateTimeInput(shipment?.deadline),
  };
}

function toRequest(values) {
  return {
    reference: values.reference.trim(),
    originId: Number(values.originId),
    destinationId: Number(values.destinationId),
    pallets: Number(values.pallets),
    weightKg: Number(values.weightKg),
    priority: values.priority,
    pinnedFirst: values.pinnedFirst,
    deadline: fromDateTimeInput(values.deadline),
  };
}

function validateDestination(value, values) {
  const missing = requiredChoice("Choose the destination warehouse")(value);
  if (missing) {
    return missing;
  }
  return value === values.originId ? ORIGIN_DIFFERS : null;
}

/**
 * Add / edit form for a shipment (`shipment` null = add). `warehouses` feed both selects;
 * `defaultOriginId` preselects the origin of a new shipment. `onSave(request)` returns a promise.
 */
export function ShipmentForm({
  shipment,
  warehouses,
  defaultOriginId,
  onSave,
  onCancel,
}) {
  // A half-filled datetime-local input reports value "" (= no deadline) with
  // validity.badInput set; the ref lets the validator tell the two apart.
  const deadlineInput = useRef(null);
  const form = useForm({
    initialValues: toFormValues(shipment, defaultOriginId),
    validate: {
      reference: textLength("Reference", 1, 40),
      originId: requiredChoice("Choose the origin warehouse"),
      destinationId: validateDestination,
      pallets: numberRange("Pallets", 1, 100, { integer: true }),
      weightKg: numberRange("Weight", 1, 100000, { integer: true }),
      deadline: () =>
        deadlineInput.current?.validity?.badInput ? INCOMPLETE_DEADLINE : null,
    },
  });
  const { formError, handleSubmit } = useFormSubmit(form, {
    onSave,
    toRequest,
    itemType: "shipment",
  });

  const warehouseOptions = [
    { value: "", label: "Select a warehouse", disabled: true },
    ...warehouses.map((warehouse) => ({
      value: String(warehouse.id),
      label: warehouse.name,
    })),
  ];

  return (
    <form onSubmit={handleSubmit} noValidate>
      <Stack gap="md">
        <TextInput
          label="Reference"
          description="e.g. PO-1100"
          withAsterisk
          data-autofocus
          {...form.getInputProps("reference")}
        />
        <SimpleGrid cols={{ base: 1, xs: 2 }} spacing="md">
          <NativeSelect
            label="From warehouse"
            withAsterisk
            data={warehouseOptions}
            {...form.getInputProps("originId")}
          />
          <NativeSelect
            label="To warehouse"
            withAsterisk
            data={warehouseOptions}
            {...form.getInputProps("destinationId")}
          />
          <NumberInput
            label="Pallets"
            description="1 to 100"
            withAsterisk
            allowDecimal={false}
            {...form.getInputProps("pallets")}
          />
          <NumberInput
            label="Weight (kg)"
            description="1 to 100 000"
            withAsterisk
            allowDecimal={false}
            {...form.getInputProps("weightKg")}
          />
        </SimpleGrid>
        <Fieldset
          legend="Priority"
          variant="unstyled"
          className="hw-form-fieldset"
        >
          <SegmentedControl
            data={PRIORITY_OPTIONS}
            value={form.getValues().priority}
            onChange={(value) => form.setFieldValue("priority", value)}
          />
        </Fieldset>
        <Checkbox
          label="Deliver this stop first"
          description="Only one pinned stop per truck; the priority plan puts it first."
          {...form.getInputProps("pinnedFirst", { type: "checkbox" })}
        />
        <TextInput
          type="datetime-local"
          label="Deadline (optional)"
          description="Local time, Warsaw"
          ref={deadlineInput}
          {...form.getInputProps("deadline")}
        />
        <FormFooter
          formError={formError}
          submitting={form.submitting}
          submitLabel="Save shipment"
          onCancel={onCancel}
        />
      </Stack>
    </form>
  );
}

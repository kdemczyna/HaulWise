import {
  Alert,
  Button,
  Checkbox,
  Group,
  NativeSelect,
  Paper,
  SimpleGrid,
  Stack,
  Switch,
  Text,
  TextInput,
  Title,
} from "@mantine/core";
import { useForm } from "@mantine/form";
import { useCallback, useRef, useState } from "react";
import { Link, useNavigate } from "react-router";
import { createPlan } from "../../api/plans.js";
import { listShipments } from "../../api/shipments.js";
import { useAsync } from "../../hooks/useAsync.js";
import {
  formatDeadline,
  formatKg,
  fromDateTimeInput,
  toDateTimeInput,
} from "../../utils/format.js";
import { applyServerErrors } from "../../utils/formErrors.js";
import { requiredChoice } from "../../utils/validation.js";
import { EmptyState } from "../common/EmptyState.jsx";
import { ListView } from "../common/ListView.jsx";
import { PinnedTag } from "../common/PinnedTag.jsx";
import { PriorityBadge } from "../common/PriorityBadge.jsx";

export const INCOMPLETE_DEPARTURE =
  "Enter both a date and a time, or clear the field";

/**
 * `shipmentIds` / `truckIds` are null while the user has not changed the selection,
 * which means "everything listed" (all preselected); otherwise arrays of id strings.
 */
function toFormValues(request) {
  return {
    depotId: request ? String(request.depotId) : "",
    shipmentIds: request ? request.shipmentIds.map(String) : null,
    truckIds: request ? request.truckIds.map(String) : null,
    returnToDepot: request?.returnToDepot ?? true,
    departureTime: toDateTimeInput(request?.departureTime),
  };
}

function idStrings(items) {
  return (items ?? []).map((item) => String(item.id));
}

/** The selected ids that are still listed (a previous run may name deleted items). */
function selectedIds(selection, items) {
  const listed = idStrings(items);
  return selection === null
    ? listed
    : selection.filter((id) => listed.includes(id));
}

function toRequest(values, shipments, trucks) {
  return {
    depotId: Number(values.depotId),
    shipmentIds: selectedIds(values.shipmentIds, shipments).map(Number),
    truckIds: selectedIds(values.truckIds, trucks).map(Number),
    returnToDepot: values.returnToDepot,
    departureTime: fromDateTimeInput(values.departureTime),
  };
}

function selectionErrors(request) {
  const errors = {};
  if (request.shipmentIds.length === 0) {
    errors.shipmentIds = "Select at least one shipment";
  }
  if (request.truckIds.length === 0) {
    errors.truckIds = "Select at least one truck";
  }
  return Object.keys(errors).length > 0 ? errors : null;
}

/**
 * Form for a planning run (US-4): depot, its shipments, trucks, return flag and departure.
 * `trucks` is the useAsync result of the truck list; `initialRequest` prefills the form
 * ("Edit run" on the result page). Navigates to the result page after a successful plan.
 */
export function PlanForm({ warehouses, trucks, initialRequest }) {
  const navigate = useNavigate();
  const departureInput = useRef(null);
  const [formError, setFormError] = useState(null);
  const form = useForm({
    mode: "controlled",
    initialValues: toFormValues(initialRequest),
    validate: {
      depotId: requiredChoice("Choose the depot"),
      departureTime: () =>
        departureInput.current?.validity?.badInput
          ? INCOMPLETE_DEPARTURE
          : null,
    },
  });
  const { depotId } = form.values;

  const loadShipments = useCallback(
    () =>
      depotId
        ? listShipments({ originId: Number(depotId) })
        : Promise.resolve([]),
    [depotId],
  );
  const shipments = useAsync(loadShipments);

  const handleSubmit = form.onSubmit(async (values) => {
    setFormError(null);
    const request = toRequest(values, shipments.data, trucks.data);
    const errors = selectionErrors(request);
    if (errors) {
      form.setErrors(errors);
      return;
    }
    try {
      const plan = await createPlan(request);
      navigate(`/plans/${plan.id}`);
    } catch (error) {
      setFormError(applyServerErrors(form, error, "plan"));
    }
  });

  const changeDepot = (event) => {
    form.setFieldValue("depotId", event.currentTarget.value);
    form.setFieldValue("shipmentIds", null);
    form.clearFieldError("shipmentIds");
  };

  const depotOptions = [
    { value: "", label: "Select a depot", disabled: true },
    ...warehouses.map((warehouse) => ({
      value: String(warehouse.id),
      label: `${warehouse.name} (${warehouse.city})`,
    })),
  ];
  const loading = shipments.loading || trucks.loading;

  return (
    <form onSubmit={handleSubmit} noValidate>
      <Stack gap="lg">
        <Paper withBorder p="lg">
          <SimpleGrid cols={{ base: 1, sm: 3 }} spacing="md">
            <NativeSelect
              label="Depot"
              description="All shipments start here"
              withAsterisk
              data={depotOptions}
              {...form.getInputProps("depotId")}
              onChange={changeDepot}
            />
            <TextInput
              type="datetime-local"
              label="Departure (optional)"
              description="Local time, Warsaw. Empty = tomorrow 06:00"
              ref={departureInput}
              {...form.getInputProps("departureTime")}
            />
            <Switch
              mt={{ sm: "xl" }}
              label="Return to depot"
              description="Trucks drive back after the last stop"
              {...form.getInputProps("returnToDepot", { type: "checkbox" })}
            />
          </SimpleGrid>
        </Paper>

        <SelectionSection
          title="Shipments"
          field="shipmentIds"
          form={form}
          result={shipments}
          label="shipments"
          renderLabel={shipmentLabel}
          empty={
            depotId ? (
              <EmptyState
                title="No shipments from this depot"
                message="Add shipments that start at this depot first."
                action={
                  <Button component={Link} to="/shipments" variant="default">
                    Go to Shipments
                  </Button>
                }
              />
            ) : (
              <Text c="dimmed">Choose a depot to see its shipments.</Text>
            )
          }
        />

        <SelectionSection
          title="Trucks"
          field="truckIds"
          form={form}
          result={trucks}
          label="trucks"
          renderLabel={truckLabel}
          empty={
            <EmptyState
              title="No trucks yet"
              message="Add a truck before planning a run."
              action={
                <Button component={Link} to="/trucks" variant="default">
                  Go to Trucks
                </Button>
              }
            />
          }
        />

        {formError && (
          <Alert color="red" variant="light" role="alert">
            {formError}
          </Alert>
        )}
        <Group justify="flex-end">
          <Button type="submit" loading={form.submitting} disabled={loading}>
            Plan the run
          </Button>
        </Group>
      </Stack>
    </form>
  );
}

/** A titled checkbox list (all preselected) with Select all / Clear and a selected count. */
function SelectionSection({
  title,
  field,
  form,
  result,
  label,
  renderLabel,
  empty,
}) {
  const selection = form.values[field];
  const items = result.data ?? [];
  const selected = selectedIds(selection, items);
  const setSelection = (ids) => {
    form.setFieldValue(field, ids);
    form.clearFieldError(field);
  };

  return (
    <Paper
      withBorder
      p="lg"
      component="section"
      aria-labelledby={`${field}-title`}
    >
      <Group justify="space-between" mb="sm" wrap="wrap">
        <Title order={2} size="h4">
          <span id={`${field}-title`}>{title}</span>
          {items.length > 0 && (
            <Text span c="dimmed" fw={400} size="sm" ml="xs">
              {selected.length} of {items.length} selected
            </Text>
          )}
        </Title>
        {items.length > 0 && (
          <Group gap="xs">
            <Button
              variant="subtle"
              size="xs"
              onClick={() => setSelection(idStrings(items))}
            >
              Select all {label}
            </Button>
            <Button variant="subtle" size="xs" onClick={() => setSelection([])}>
              Clear {label}
            </Button>
          </Group>
        )}
      </Group>
      <ListView result={result} label={label} empty={empty}>
        {(list) => (
          <Checkbox.Group
            aria-label={title}
            value={selected}
            onChange={setSelection}
            error={form.errors[field]}
          >
            <Stack gap="xs" mt="xs">
              {list.map((item) => (
                <Checkbox
                  key={item.id}
                  value={String(item.id)}
                  label={renderLabel(item)}
                />
              ))}
            </Stack>
          </Checkbox.Group>
        )}
      </ListView>
    </Paper>
  );
}

function shipmentLabel(shipment) {
  const deadline = shipment.deadline
    ? `, due ${formatDeadline(shipment.deadline)}`
    : "";
  return (
    <Group gap="xs" wrap="wrap">
      <Text span fw={600} size="sm">
        {shipment.reference}
      </Text>
      <Text span size="sm" c="dimmed">
        to {shipment.destination.city}, {shipment.pallets} pallets,{" "}
        {formatKg(shipment.weightKg)}
        {deadline}
      </Text>
      <PriorityBadge priority={shipment.priority} />
      {shipment.pinnedFirst && <PinnedTag />}
    </Group>
  );
}

function truckLabel(truck) {
  return (
    <Group gap="xs" wrap="wrap">
      <Text span fw={600} size="sm">
        {truck.plate}
      </Text>
      <Text span size="sm" c="dimmed">
        {truck.capacityPallets} pallets, {formatKg(truck.payloadKg)}
      </Text>
    </Group>
  );
}

import { NumberInput, SimpleGrid, Stack, TextInput } from "@mantine/core";
import { useForm } from "@mantine/form";
import { useFormSubmit } from "../../hooks/useFormSubmit.js";
import { numberRange, textLength } from "../../utils/validation.js";
import { FormFooter } from "./FormFooter.jsx";

const NUMBER_FIELDS = [
  "capacityPallets",
  "payloadKg",
  "baseConsumptionLPer100Km",
  "extraConsumptionFullLoadLPer100Km",
  "driverCostPlnPerHour",
];

function toFormValues(truck) {
  const values = { plate: truck?.plate ?? "" };
  for (const field of NUMBER_FIELDS) {
    values[field] = truck?.[field] ?? "";
  }
  return values;
}

function toRequest(values) {
  const request = { plate: values.plate.trim() };
  for (const field of NUMBER_FIELDS) {
    request[field] = Number(values[field]);
  }
  return request;
}

/** Add / edit form for a truck (`truck` null = add). `onSave(request)` returns a promise. */
export function TruckForm({ truck, onSave, onCancel }) {
  const form = useForm({
    initialValues: toFormValues(truck),
    validate: {
      plate: textLength("Plate", 2, 15),
      capacityPallets: numberRange("Capacity", 1, 40, { integer: true }),
      payloadKg: numberRange("Payload", 1, 40000, { integer: true }),
      baseConsumptionLPer100Km: numberRange("Base consumption", 0, 100, {
        minExclusive: true,
      }),
      extraConsumptionFullLoadLPer100Km: numberRange(
        "Extra consumption",
        0,
        50,
      ),
      driverCostPlnPerHour: numberRange("Driver cost", 0, 1000),
    },
  });
  const { formError, handleSubmit } = useFormSubmit(form, {
    onSave,
    toRequest,
    itemType: "truck",
  });

  return (
    <form onSubmit={handleSubmit} noValidate>
      <Stack gap="md">
        <TextInput
          label="Plate"
          description="Stored in capitals, e.g. WX 1001A"
          withAsterisk
          data-autofocus
          {...form.getInputProps("plate")}
        />
        <SimpleGrid cols={{ base: 1, xs: 2 }} spacing="md">
          <NumberInput
            label="Capacity (pallets)"
            description="1 to 40"
            withAsterisk
            allowDecimal={false}
            {...form.getInputProps("capacityPallets")}
          />
          <NumberInput
            label="Payload (kg)"
            description="1 to 40 000"
            withAsterisk
            allowDecimal={false}
            {...form.getInputProps("payloadKg")}
          />
          <NumberInput
            label="Base consumption (L/100 km)"
            description="Empty truck, above 0 up to 100"
            withAsterisk
            decimalScale={2}
            {...form.getInputProps("baseConsumptionLPer100Km")}
          />
          <NumberInput
            label="Extra consumption at full load (L/100 km)"
            description="0 to 50"
            withAsterisk
            decimalScale={2}
            {...form.getInputProps("extraConsumptionFullLoadLPer100Km")}
          />
          <NumberInput
            label="Driver cost (PLN/h)"
            description="0 to 1000"
            withAsterisk
            decimalScale={2}
            {...form.getInputProps("driverCostPlnPerHour")}
          />
        </SimpleGrid>
        <FormFooter
          formError={formError}
          submitting={form.submitting}
          submitLabel="Save truck"
          onCancel={onCancel}
        />
      </Stack>
    </form>
  );
}

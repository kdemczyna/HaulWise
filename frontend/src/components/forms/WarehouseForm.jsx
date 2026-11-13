import { NumberInput, SimpleGrid, Stack, TextInput } from "@mantine/core";
import { useForm } from "@mantine/form";
import { useFormSubmit } from "../../hooks/useFormSubmit.js";
import { numberRange, textLength } from "../../utils/validation.js";
import { FormFooter } from "./FormFooter.jsx";

function toFormValues(warehouse) {
  return {
    name: warehouse?.name ?? "",
    city: warehouse?.city ?? "",
    lat: warehouse?.lat ?? "",
    lon: warehouse?.lon ?? "",
  };
}

function toRequest(values) {
  return {
    name: values.name.trim(),
    city: values.city.trim(),
    lat: Number(values.lat),
    lon: Number(values.lon),
  };
}

/** Add / edit form for a warehouse (`warehouse` null = add). `onSave(request)` returns a promise. */
export function WarehouseForm({ warehouse, onSave, onCancel }) {
  const form = useForm({
    initialValues: toFormValues(warehouse),
    validate: {
      name: textLength("Name", 2, 60),
      city: textLength("City", 2, 60),
      lat: numberRange("Latitude", -90, 90),
      lon: numberRange("Longitude", -180, 180),
    },
  });
  const { formError, handleSubmit } = useFormSubmit(form, {
    onSave,
    toRequest,
    itemType: "warehouse",
  });

  return (
    <form onSubmit={handleSubmit} noValidate>
      <Stack gap="md">
        <TextInput
          label="Name"
          description="Unique, e.g. WAW Central"
          withAsterisk
          data-autofocus
          {...form.getInputProps("name")}
        />
        <TextInput label="City" withAsterisk {...form.getInputProps("city")} />
        <SimpleGrid cols={{ base: 1, xs: 2 }} spacing="md">
          <NumberInput
            label="Latitude"
            description="-90 to 90"
            withAsterisk
            decimalScale={6}
            hideControls
            {...form.getInputProps("lat")}
          />
          <NumberInput
            label="Longitude"
            description="-180 to 180"
            withAsterisk
            decimalScale={6}
            hideControls
            {...form.getInputProps("lon")}
          />
        </SimpleGrid>
        <FormFooter
          formError={formError}
          submitting={form.submitting}
          submitLabel="Save warehouse"
          onCancel={onCancel}
        />
      </Stack>
    </form>
  );
}

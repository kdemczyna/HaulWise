import {
  Alert,
  Button,
  Group,
  NumberInput,
  SimpleGrid,
  Stack,
  Switch,
  Title,
} from "@mantine/core";
import { useForm } from "@mantine/form";
import { useFormSubmit } from "../../hooks/useFormSubmit.js";
import { numberRange } from "../../utils/validation.js";

function toFormValues(settings) {
  return {
    dieselPricePlnPerLitre: settings.dieselPricePlnPerLitre,
    averageSpeedKmh: settings.averageSpeedKmh,
    roadFactor: settings.roadFactor,
    serviceMinutesPerStop: settings.serviceMinutesPerStop,
    urgentWeightPlnPerHour: settings.urgentWeightPlnPerHour,
    highWeightPlnPerHour: settings.highWeightPlnPerHour,
    complianceEnabled: settings.complianceEnabled,
    maxContinuousDrivingMinutes: settings.maxContinuousDrivingMinutes,
    breakMinutes: settings.breakMinutes,
    maxDailyDrivingMinutes: settings.maxDailyDrivingMinutes,
    consolidationFillThresholdPct: settings.consolidationFillThresholdPct,
  };
}

function toRequest(values) {
  return {
    dieselPricePlnPerLitre: Number(values.dieselPricePlnPerLitre),
    averageSpeedKmh: Number(values.averageSpeedKmh),
    roadFactor: Number(values.roadFactor),
    serviceMinutesPerStop: Number(values.serviceMinutesPerStop),
    urgentWeightPlnPerHour: Number(values.urgentWeightPlnPerHour),
    highWeightPlnPerHour: Number(values.highWeightPlnPerHour),
    complianceEnabled: values.complianceEnabled,
    maxContinuousDrivingMinutes: Number(values.maxContinuousDrivingMinutes),
    breakMinutes: Number(values.breakMinutes),
    maxDailyDrivingMinutes: Number(values.maxDailyDrivingMinutes),
    consolidationFillThresholdPct: Number(values.consolidationFillThresholdPct),
  };
}

/**
 * Editable form for the whole settings row (US-15): the backend's {@code SettingsRequest} ranges, mirrored
 * here for fast feedback (ARCHITECTURE 6.4). `onSave(request)` returns a promise and resolves to the saved
 * settings; the caller shows the success notification.
 */
export function SettingsForm({ settings, onSave }) {
  const form = useForm({
    initialValues: toFormValues(settings),
    validate: {
      dieselPricePlnPerLitre: numberRange("Diesel price", 0, 50, {
        minExclusive: true,
      }),
      averageSpeedKmh: numberRange("Average speed", 10, 90),
      roadFactor: numberRange("Road factor", 1, 3),
      serviceMinutesPerStop: numberRange("Service time", 0, 480, {
        integer: true,
      }),
      urgentWeightPlnPerHour: (value, values) => {
        const rangeError = numberRange("Urgent weight", 0, 10000)(value);
        if (rangeError) {
          return rangeError;
        }
        return Number(value) < Number(values.highWeightPlnPerHour)
          ? "Urgent weight must be greater than or equal to high weight"
          : null;
      },
      highWeightPlnPerHour: numberRange("High weight", 0, 10000),
      maxContinuousDrivingMinutes: (value, values) => {
        const rangeError = numberRange("Max continuous driving", 30, 900, {
          integer: true,
        })(value);
        if (rangeError) {
          return rangeError;
        }
        return Number(value) > Number(values.maxDailyDrivingMinutes)
          ? "Max continuous driving must be less than or equal to max daily driving"
          : null;
      },
      breakMinutes: numberRange("Break length", 15, 180, { integer: true }),
      maxDailyDrivingMinutes: numberRange("Max daily driving", 60, 900, {
        integer: true,
      }),
      consolidationFillThresholdPct: numberRange("Fill threshold", 1, 100, {
        integer: true,
      }),
    },
  });
  const { formError, handleSubmit } = useFormSubmit(form, {
    onSave,
    toRequest,
    itemType: "settings",
  });

  return (
    <form onSubmit={handleSubmit} noValidate>
      <Stack gap="xl">
        <Stack gap="md" component="section" aria-labelledby="costs-title">
          <Title order={2} size="h4" id="costs-title">
            Costs
          </Title>
          <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="md">
            <NumberInput
              label="Diesel price (PLN/L)"
              description="Above 0, up to 50"
              withAsterisk
              decimalScale={2}
              {...form.getInputProps("dieselPricePlnPerLitre")}
            />
            <NumberInput
              label="Road factor"
              description="1 to 3, road distance / straight line"
              withAsterisk
              decimalScale={2}
              {...form.getInputProps("roadFactor")}
            />
            <NumberInput
              label="Average speed (km/h)"
              description="10 to 90, used for estimated legs"
              withAsterisk
              decimalScale={1}
              {...form.getInputProps("averageSpeedKmh")}
            />
            <NumberInput
              label="Service time per stop (min)"
              description="0 to 480"
              withAsterisk
              allowDecimal={false}
              {...form.getInputProps("serviceMinutesPerStop")}
            />
          </SimpleGrid>
        </Stack>

        <Stack gap="md" component="section" aria-labelledby="priority-title">
          <Title order={2} size="h4" id="priority-title">
            Priority weights
          </Title>
          <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="md">
            <NumberInput
              label="Urgent weight (PLN/h)"
              description="0 to 10 000, must be >= high weight"
              withAsterisk
              decimalScale={2}
              {...form.getInputProps("urgentWeightPlnPerHour")}
            />
            <NumberInput
              label="High weight (PLN/h)"
              description="0 to 10 000"
              withAsterisk
              decimalScale={2}
              {...form.getInputProps("highWeightPlnPerHour")}
            />
          </SimpleGrid>
        </Stack>

        <Stack gap="md" component="section" aria-labelledby="compliance-title">
          <Title order={2} size="h4" id="compliance-title">
            Compliance
          </Title>
          <Switch
            label="Enforce EU driving-time limits"
            {...form.getInputProps("complianceEnabled", { type: "checkbox" })}
          />
          <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="md">
            <NumberInput
              label="Max continuous driving (min)"
              description="30 to 900, must be <= max daily driving"
              withAsterisk
              allowDecimal={false}
              {...form.getInputProps("maxContinuousDrivingMinutes")}
            />
            <NumberInput
              label="Break length (min)"
              description="15 to 180"
              withAsterisk
              allowDecimal={false}
              {...form.getInputProps("breakMinutes")}
            />
            <NumberInput
              label="Max daily driving (min)"
              description="60 to 900"
              withAsterisk
              allowDecimal={false}
              {...form.getInputProps("maxDailyDrivingMinutes")}
            />
          </SimpleGrid>
        </Stack>

        <Stack
          gap="md"
          component="section"
          aria-labelledby="consolidation-title"
        >
          <Title order={2} size="h4" id="consolidation-title">
            Consolidation
          </Title>
          <NumberInput
            label="Fill threshold (%)"
            description="1 to 100"
            withAsterisk
            allowDecimal={false}
            maw={240}
            {...form.getInputProps("consolidationFillThresholdPct")}
          />
        </Stack>

        {formError && (
          <Alert color="red" variant="light" role="alert">
            {formError}
          </Alert>
        )}
        <Group justify="flex-end">
          <Button type="submit" loading={form.submitting}>
            Save settings
          </Button>
        </Group>
      </Stack>
    </form>
  );
}

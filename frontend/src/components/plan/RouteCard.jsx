import {
  Badge,
  ColorSwatch,
  Group,
  Paper,
  SimpleGrid,
  Stack,
  Text,
  Title,
} from "@mantine/core";
import { routeColor } from "../../utils/colors.js";
import {
  formatDateTime,
  formatDuration,
  formatInteger,
  formatKg,
  formatKm,
  formatPln,
} from "../../utils/format.js";
import { StopTimeline } from "./StopTimeline.jsx";
import { UtilisationBar } from "./UtilisationBar.jsx";

/**
 * One truck's route in the selected variant. When the route is the same in both plans
 * (`sameInBoth`) the stop list is collapsed behind a disclosure.
 */
export function RouteCard({ route, variant, sameInBoth }) {
  const color = routeColor(route.colorIndex);
  const { utilisation, totals } = route;
  const stopCount = `${route.stops.length} ${route.stops.length === 1 ? "stop" : "stops"}`;

  return (
    <Paper
      withBorder
      p="md"
      component="article"
      className="hw-route-card"
      style={{ "--hw-route-color": color }}
      aria-label={`Truck ${route.plate}`}
    >
      <Stack gap="md">
        <Group justify="space-between" wrap="wrap" gap="xs">
          <Group gap="sm">
            <ColorSwatch color={color} size={16} aria-hidden="true" />
            <Title order={3} size="h4">
              {route.plate}
            </Title>
            {sameInBoth && (
              <Badge variant="light" radius="xl" tt="none">
                Same in both plans
              </Badge>
            )}
          </Group>
          <Text size="sm" c="dimmed" className="hw-numeric">
            {stopCount}, {formatKm(totals.distanceKm)},{" "}
            {formatDuration(totals.totalMinutes)}, {formatPln(totals.costPln)}
          </Text>
        </Group>
        <SimpleGrid cols={{ base: 1, xs: 2 }} spacing="md">
          <UtilisationBar
            label="Pallets"
            used={utilisation.pallets}
            capacity={utilisation.capacityPallets}
            percent={utilisation.palletsPct}
            format={formatInteger}
          />
          <UtilisationBar
            label="Weight"
            used={utilisation.weightKg}
            capacity={utilisation.payloadKg}
            percent={utilisation.weightPct}
            format={formatKg}
          />
        </SimpleGrid>
        {sameInBoth ? (
          <details className="hw-route-details">
            <summary>Show {stopCount}</summary>
            <StopTimeline stops={route.stops} variant={variant} />
          </details>
        ) : (
          <StopTimeline stops={route.stops} variant={variant} />
        )}
        <Text size="sm" c="dimmed">
          Departs {formatDateTime(route.departureTime)}
          {route.returnTime
            ? `, back at the depot ${formatDateTime(route.returnTime)}`
            : ", does not return to the depot"}
        </Text>
      </Stack>
    </Paper>
  );
}

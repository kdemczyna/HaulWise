import { SegmentedControl, Group, Stack, Text, Title } from "@mantine/core";
import { useState } from "react";
import {
  VARIANT_LABELS,
  VARIANTS,
  routeOfTruck,
  sameRoute,
} from "../../utils/plan.js";
import { RouteCard } from "./RouteCard.jsx";
import { RouteMap } from "./RouteMap.jsx";

const VARIANT_OPTIONS = VARIANTS.map((value) => ({
  value,
  label: VARIANT_LABELS[value],
}));

/**
 * Map and truck cards of one variant, chosen with an Optimal / Priority toggle.
 * `unusedPlates` names the selected trucks that got no shipment.
 */
export function RoutesSection({ plan, unusedPlates }) {
  const [variant, setVariant] = useState("optimal");
  const other = variant === "optimal" ? "priority" : "optimal";
  const routes = plan[variant].routes;
  const allStops = VARIANTS.flatMap((name) =>
    plan[name].routes.flatMap((route) => route.stops),
  );
  const label = VARIANT_LABELS[variant].toLowerCase();

  return (
    <section aria-labelledby="routes-title">
      <Group justify="space-between" mb="sm" wrap="wrap">
        <Title order={2} size="h4" id="routes-title">
          Routes
        </Title>
        <SegmentedControl
          aria-label="Plan shown on the map and in the truck cards"
          data={VARIANT_OPTIONS}
          value={variant}
          onChange={setVariant}
        />
      </Group>
      <Stack gap="md">
        <RouteMap
          depot={plan.depot}
          routes={routes}
          allStops={allStops}
          label={`Map of the ${label} routes`}
        />
        {routes.length === 0 && (
          <Text c="dimmed">No truck was loaded in this plan.</Text>
        )}
        {routes.map((route) => (
          <RouteCard
            key={route.truckId}
            route={route}
            variant={variant}
            sameInBoth={sameRoute(
              route,
              routeOfTruck(plan[other], route.truckId),
            )}
          />
        ))}
        {unusedPlates.length > 0 && (
          <Text c="dimmed">Not used: {unusedPlates.join(", ")}</Text>
        )}
      </Stack>
    </section>
  );
}

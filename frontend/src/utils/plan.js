/** Pure helpers for the plan response (ARCHITECTURE section 5, "Plan response rules"). */
import { minutesBetween } from "./format.js";

export const VARIANTS = ["optimal", "priority"];

export const VARIANT_LABELS = { optimal: "Optimal", priority: "Priority" };

/** True when nothing was loaded, so the two variants have nothing to compare (6.5). */
export function nothingToCompare(plan) {
  return plan.optimal.routes.length === 0 && plan.priority.routes.length === 0;
}

/** The route of `truckId` in a variant, or undefined. */
export function routeOfTruck(variant, truckId) {
  return variant.routes.find((route) => route.truckId === truckId);
}

/** Same warehouses in the same order (and the same return) in both variants. */
export function sameRoute(a, b) {
  if (!a || !b || a.stops.length !== b.stops.length) {
    return false;
  }
  const sameStops = a.stops.every(
    (stop, index) => stop.warehouseId === b.stops[index].warehouseId,
  );
  return sameStops && Boolean(a.returnLeg) === Boolean(b.returnLeg);
}

/** Minutes a stop is late (> 0), or 0 when on time / without a deadline. */
export function minutesLate(stop) {
  if (!stop.deadline) {
    return 0;
  }
  return Math.max(0, minutesBetween(stop.deadline, stop.arrivalTime) ?? 0);
}

/** Selected truck ids that got no route in either variant, in request order. */
export function unusedTruckIds(plan) {
  const used = new Set(
    VARIANTS.flatMap((variant) =>
      plan[variant].routes.map((route) => route.truckId),
    ),
  );
  return plan.request.truckIds.filter((id) => !used.has(id));
}

/**
 * Plan warnings plus the route warnings of both variants, each with a stable key and
 * the variant it belongs to (null for plan-level warnings).
 */
export function allWarnings(plan) {
  const planLevel = plan.warnings.map((warning, index) => ({
    ...warning,
    key: `plan-${index}`,
    variant: null,
  }));
  const routeLevel = VARIANTS.flatMap((variant) =>
    plan[variant].routes.flatMap((route) =>
      route.warnings.map((warning, index) => ({
        ...warning,
        key: `${variant}-${route.truckId}-${index}`,
        variant,
      })),
    ),
  );
  return [...planLevel, ...routeLevel];
}

/** Trucks and shipment count that sending `variant` ("optimal" | "priority") affects. */
export function dispatchScope(plan, variant) {
  const routes = plan[variant].routes;
  return {
    plates: routes.map((route) => route.plate),
    shipmentCount: routes.reduce(
      (count, route) =>
        count +
        route.stops.reduce((sum, stop) => sum + stop.shipmentIds.length, 0),
      0,
    ),
  };
}

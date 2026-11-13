import { allWarnings, minutesLate, sameRoute, unusedTruckIds } from "./plan.js";

const stop = (warehouseId) => ({ warehouseId });

describe("minutesLate", () => {
  it("counts lateness across midnight", () => {
    expect(
      minutesLate({
        deadline: "2026-11-13T23:50:00",
        arrivalTime: "2026-11-14T00:10:00",
      }),
    ).toBe(20);
  });

  it("is 0 when on time or without a deadline", () => {
    expect(
      minutesLate({
        deadline: "2026-11-13T12:00:00",
        arrivalTime: "2026-11-13T10:51:00",
      }),
    ).toBe(0);
    expect(
      minutesLate({ deadline: null, arrivalTime: "2026-11-13T10:51:00" }),
    ).toBe(0);
  });
});

describe("sameRoute", () => {
  const a = { stops: [stop(2), stop(3)], returnLeg: { distanceKm: 10 } };

  it("is true for the same stops in the same order and the same return", () => {
    expect(
      sameRoute(a, {
        stops: [stop(2), stop(3)],
        returnLeg: { distanceKm: 10 },
      }),
    ).toBe(true);
  });

  it("is false for a different order or a different return", () => {
    expect(
      sameRoute(a, {
        stops: [stop(3), stop(2)],
        returnLeg: { distanceKm: 10 },
      }),
    ).toBe(false);
    expect(sameRoute(a, { stops: [stop(2), stop(3)], returnLeg: null })).toBe(
      false,
    );
    expect(sameRoute(a, undefined)).toBe(false);
  });
});

describe("unusedTruckIds and allWarnings", () => {
  const plan = {
    request: { truckIds: [5, 1, 2, 3] },
    warnings: [{ code: "ESTIMATED_DISTANCE" }],
    optimal: {
      routes: [{ truckId: 1, warnings: [{ code: "DEADLINE_MISSED" }] }],
    },
    priority: { routes: [{ truckId: 2, warnings: [] }] },
  };

  it("lists selected trucks without a route in either variant, in request order", () => {
    expect(unusedTruckIds(plan)).toEqual([5, 3]);
  });

  it("merges plan and route warnings with their variant", () => {
    expect(allWarnings(plan).map((w) => [w.code, w.variant])).toEqual([
      ["ESTIMATED_DISTANCE", null],
      ["DEADLINE_MISSED", "optimal"],
    ]);
  });
});

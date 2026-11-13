/**
 * Trimmed copy of the real seed demo response (POST /api/plans, depot WAW Central, all its
 * shipments and trucks): WX 1001A differs between the variants (pinned Gdansk), WX 2002B
 * is the same in both, PO-1099 is not loaded, truck 3 got nothing. Totals and delta are the
 * real ones (they cover the routes left out here).
 */
const DEPARTURE = "2026-11-14T06:00:00";

function stop(fields) {
  return {
    shipmentIds: [],
    dropWeightKg: 3000,
    pinned: false,
    legDistanceKm: 100,
    legDurationMinutes: 90,
    legLitres: 30,
    legCostPln: 250,
    legSource: "SEEDED",
    loadWeightKgOnLeg: 10000,
    deadline: null,
    deadlineMissed: false,
    ...fields,
  };
}

const LUBLIN = {
  warehouseId: 8,
  name: "LUB East",
  city: "Lublin",
  lat: 51.2465,
  lon: 22.5684,
  dropPallets: 6,
  priority: "URGENT",
  deadline: "2026-11-14T20:00:00",
};
const KRAKOW = {
  warehouseId: 2,
  name: "KRK South",
  city: "Krakow",
  lat: 50.0647,
  lon: 19.945,
  dropPallets: 6,
  priority: "HIGH",
  deadline: "2026-11-15T12:00:00",
};
const GDANSK = {
  warehouseId: 3,
  name: "GDN Port",
  city: "Gdansk",
  lat: 54.352,
  lon: 18.6466,
  dropPallets: 12,
  priority: "HIGH",
  pinned: true,
  deadline: "2026-11-14T12:00:00",
};

const UTILISATION_1 = {
  pallets: 32,
  capacityPallets: 33,
  weightKg: 20200,
  payloadKg: 24000,
  palletsPct: 97.0,
  weightPct: 84.2,
};

const ROUTE_TOTALS = {
  distanceKm: 1400,
  drivingMinutes: 1239,
  totalMinutes: 1359,
  litres: 445.3,
  fuelCostPln: 2760.86,
  driverCostPln: 1019.25,
  costPln: 3780.11,
};

const truck2 = {
  truckId: 2,
  plate: "WX 2002B",
  colorIndex: 1,
  utilisation: {
    pallets: 30,
    capacityPallets: 33,
    weightKg: 20800,
    payloadKg: 24000,
    palletsPct: 90.9,
    weightPct: 86.7,
  },
  departureTime: DEPARTURE,
  returnTime: "2026-11-14T22:09:00",
  stops: [
    stop({
      sequence: 1,
      warehouseId: 4,
      name: "POZ West",
      city: "Poznan",
      lat: 52.4064,
      lon: 16.9252,
      dropPallets: 16,
      priority: "NORMAL",
      arrivalTime: "2026-11-14T10:26:00",
      departureTime: "2026-11-14T10:56:00",
    }),
  ],
  returnLeg: {
    distanceKm: 295.0,
    durationMinutes: 253,
    litres: 85.6,
    costPln: 720.16,
    source: "SEEDED",
  },
  totals: ROUTE_TOTALS,
  warnings: [],
};

const RETURN_LEG = {
  distanceKm: 340.0,
  durationMinutes: 291,
  litres: 95.2,
  costPln: 808.49,
  source: "SEEDED",
};

export const DEMO_PLAN = {
  id: "91eed3bb-a328-4f46-bd7f-0e74a2030492",
  createdAt: "2026-11-13T10:00:00",
  dispatchedAt: null,
  request: {
    depotId: 1,
    shipmentIds: [1, 2, 3, 4, 11],
    truckIds: [1, 2, 3],
    returnToDepot: true,
    departureTime: DEPARTURE,
  },
  depot: {
    id: 1,
    name: "WAW Central",
    city: "Warsaw",
    lat: 52.2297,
    lon: 21.0122,
  },
  optimal: {
    mode: "OPTIMAL",
    totals: {
      distanceKm: 3750.0,
      drivingMinutes: 3240,
      totalMinutes: 3510,
      litres: 1164.1,
      fuelCostPln: 7217.38,
      driverCostPln: 2632.5,
      costPln: 9849.88,
    },
    deadlinesMissed: 1,
    routes: [
      {
        truckId: 1,
        plate: "WX 1001A",
        colorIndex: 0,
        utilisation: UTILISATION_1,
        departureTime: DEPARTURE,
        returnTime: "2026-11-15T04:39:00",
        stops: [
          stop({
            ...LUBLIN,
            sequence: 1,
            arrivalTime: "2026-11-14T08:26:00",
            departureTime: "2026-11-14T08:56:00",
          }),
          stop({
            ...KRAKOW,
            sequence: 2,
            arrivalTime: "2026-11-14T13:26:00",
            departureTime: "2026-11-14T13:56:00",
          }),
          stop({
            ...GDANSK,
            sequence: 3,
            arrivalTime: "2026-11-14T23:18:00",
            departureTime: "2026-11-14T23:48:00",
            deadlineMissed: true,
          }),
        ],
        returnLeg: RETURN_LEG,
        totals: ROUTE_TOTALS,
        warnings: [
          {
            code: "DEADLINE_MISSED",
            severity: "WARNING",
            message:
              "WX 1001A arrives in Gdansk at 2026-11-14 23:18, after the deadline of PO-1042 (2026-11-14 12:00).",
            truckId: 1,
            shipmentIds: [2],
          },
        ],
      },
      truck2,
    ],
    warnings: [],
  },
  priority: {
    mode: "PRIORITY",
    totals: {
      distanceKm: 3835.0,
      drivingMinutes: 3315,
      totalMinutes: 3585,
      litres: 1202.3,
      fuelCostPln: 7454.11,
      driverCostPln: 2688.75,
      costPln: 10142.86,
    },
    deadlinesMissed: 0,
    routes: [
      {
        truckId: 1,
        plate: "WX 1001A",
        colorIndex: 0,
        utilisation: UTILISATION_1,
        departureTime: DEPARTURE,
        returnTime: "2026-11-15T05:54:00",
        stops: [
          stop({
            ...GDANSK,
            sequence: 1,
            arrivalTime: "2026-11-14T10:51:00",
            departureTime: "2026-11-14T11:21:00",
          }),
          stop({
            ...LUBLIN,
            sequence: 2,
            arrivalTime: "2026-11-14T18:36:00",
            departureTime: "2026-11-14T19:06:00",
          }),
          stop({
            ...KRAKOW,
            sequence: 3,
            arrivalTime: "2026-11-14T23:36:00",
            departureTime: "2026-11-15T00:06:00",
          }),
        ],
        returnLeg: { ...RETURN_LEG, distanceKm: 290.0 },
        totals: ROUTE_TOTALS,
        warnings: [],
      },
      truck2,
    ],
    warnings: [],
  },
  delta: {
    distanceKm: 85.0,
    durationMinutes: 75,
    litres: 38.2,
    fuelCostPln: 236.72,
    driverCostPln: 56.25,
    costPln: 292.97,
    noExtraCost: false,
    sentence:
      "Delivering Gdansk first costs 292.97 PLN more (85 km and 1 h 15 min longer).",
  },
  unassigned: [
    {
      shipmentId: 11,
      reference: "PO-1099",
      reason: "EXCEEDS_LARGEST_TRUCK",
      message: "36 pallets exceed the largest selected truck (33 pallets).",
    },
  ],
  warnings: [],
};

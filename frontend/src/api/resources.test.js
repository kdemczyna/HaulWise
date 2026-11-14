import { request } from "./client.js";
import { getDistances, refreshDistances } from "./distances.js";
import { createPlan, dispatchPlan, getPlan, listPlans } from "./plans.js";
import { getSettings, updateSettings } from "./settings.js";
import {
  createShipment,
  deleteShipment,
  listShipments,
  updateShipment,
} from "./shipments.js";
import { deleteTruck, listTrucks, setTruckAvailable } from "./trucks.js";
import {
  createWarehouse,
  getWarehouse,
  listWarehouses,
  updateWarehouse,
} from "./warehouses.js";

vi.mock("./client.js", () => ({ request: vi.fn() }));

describe("resource functions", () => {
  beforeEach(() => {
    vi.mocked(request).mockReset().mockResolvedValue("result");
  });

  it.each([
    ["listWarehouses", () => listWarehouses(), ["GET", "/warehouses"]],
    ["getWarehouse", () => getWarehouse(3), ["GET", "/warehouses/3"]],
    [
      "createWarehouse",
      () => createWarehouse({ name: "A" }),
      ["POST", "/warehouses", { name: "A" }],
    ],
    [
      "updateWarehouse",
      () => updateWarehouse(3, { name: "B" }),
      ["PUT", "/warehouses/3", { name: "B" }],
    ],
    ["listTrucks", () => listTrucks(), ["GET", "/trucks"]],
    ["deleteTruck", () => deleteTruck(5), ["DELETE", "/trucks/5"]],
    [
      "createShipment",
      () => createShipment({ reference: "PO-1" }),
      ["POST", "/shipments", { reference: "PO-1" }],
    ],
    [
      "updateShipment",
      () => updateShipment(9, { reference: "PO-2" }),
      ["PUT", "/shipments/9", { reference: "PO-2" }],
    ],
    ["deleteShipment", () => deleteShipment(9), ["DELETE", "/shipments/9"]],
    ["getSettings", () => getSettings(), ["GET", "/settings"]],
    [
      "updateSettings",
      () => updateSettings({ roadFactor: 1.3 }),
      ["PUT", "/settings", { roadFactor: 1.3 }],
    ],
    ["getDistances", () => getDistances(), ["GET", "/distances"]],
    [
      "refreshDistances",
      () => refreshDistances(),
      ["POST", "/distances/refresh"],
    ],
    ["listPlans", () => listPlans(), ["GET", "/plans"]],
    [
      "createPlan",
      () => createPlan({ depotId: 1 }),
      ["POST", "/plans", { depotId: 1 }],
    ],
    ["getPlan", () => getPlan("a b"), ["GET", "/plans/a%20b"]],
    [
      "dispatchPlan",
      () => dispatchPlan("a b", "PRIORITY"),
      ["POST", "/plans/a%20b/dispatch", { mode: "PRIORITY" }],
    ],
  ])("%s calls the matching endpoint", async (_, call, expectedArgs) => {
    await expect(call()).resolves.toBe("result");
    expect(request).toHaveBeenCalledWith(...expectedArgs);
  });

  it.each([
    [undefined, "/shipments"],
    [{}, "/shipments"],
    [{ priority: "", originId: "" }, "/shipments"],
    [{ priority: "URGENT" }, "/shipments?priority=URGENT"],
    [{ originId: 1 }, "/shipments?originId=1"],
    [{ priority: "HIGH", originId: 2 }, "/shipments?priority=HIGH&originId=2"],
  ])("listShipments(%o) requests %s", async (filters, expectedPath) => {
    await listShipments(filters);
    expect(request).toHaveBeenCalledWith("GET", expectedPath);
  });

  it("setTruckAvailable reloads the truck and sends the full record with the new flag", async () => {
    vi.mocked(request)
      .mockResolvedValueOnce({
        id: 5,
        plate: "PO 6006F",
        capacityPallets: 33,
        payloadKg: 24000,
        baseConsumptionLPer100Km: 28,
        extraConsumptionFullLoadLPer100Km: 10,
        driverCostPlnPerHour: 45,
        homeWarehouse: { id: 4, name: "POZ West", city: "Poznan" },
        available: true,
      })
      .mockResolvedValueOnce("saved");

    await expect(setTruckAvailable(5, false)).resolves.toBe("saved");

    expect(request).toHaveBeenNthCalledWith(1, "GET", "/trucks/5");
    expect(request).toHaveBeenNthCalledWith(2, "PUT", "/trucks/5", {
      plate: "PO 6006F",
      capacityPallets: 33,
      payloadKg: 24000,
      baseConsumptionLPer100Km: 28,
      extraConsumptionFullLoadLPer100Km: 10,
      driverCostPlnPerHour: 45,
      homeWarehouseId: 4,
      available: false,
    });
  });
});

import { request } from "./client.js";

const path = (id) => `/trucks/${encodeURIComponent(id)}`;

export const listTrucks = () => request("GET", "/trucks");
export const getTruck = (id) => request("GET", path(id));
export const createTruck = (truck) => request("POST", "/trucks", truck);
export const updateTruck = (id, truck) => request("PUT", path(id), truck);
export const deleteTruck = (id) => request("DELETE", path(id));
export const recommendTrucks = (recommendationRequest) =>
  request("POST", "/trucks/recommend", recommendationRequest);

/**
 * Marks a truck available or on route (T-33) through the full update: loads the current
 * record first, so no other field is overwritten with a stale value from the list.
 */
export async function setTruckAvailable(id, available) {
  const truck = await getTruck(id);
  return updateTruck(id, {
    plate: truck.plate,
    capacityPallets: truck.capacityPallets,
    payloadKg: truck.payloadKg,
    baseConsumptionLPer100Km: truck.baseConsumptionLPer100Km,
    extraConsumptionFullLoadLPer100Km: truck.extraConsumptionFullLoadLPer100Km,
    driverCostPlnPerHour: truck.driverCostPlnPerHour,
    homeWarehouseId: truck.homeWarehouse.id,
    available,
  });
}

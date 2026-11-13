import { request } from "./client.js";

const path = (id) => `/shipments/${encodeURIComponent(id)}`;

/** Optional filters: {priority: "URGENT"|"HIGH"|"NORMAL", originId}. Empty values are left out. */
export function listShipments({ priority, originId } = {}) {
  const query = new URLSearchParams();
  if (priority) {
    query.set("priority", priority);
  }
  if (originId !== undefined && originId !== null && originId !== "") {
    query.set("originId", String(originId));
  }
  const search = query.toString();
  return request("GET", search ? `/shipments?${search}` : "/shipments");
}

export const getShipment = (id) => request("GET", path(id));
export const createShipment = (shipment) =>
  request("POST", "/shipments", shipment);
export const updateShipment = (id, shipment) =>
  request("PUT", path(id), shipment);
export const deleteShipment = (id) => request("DELETE", path(id));

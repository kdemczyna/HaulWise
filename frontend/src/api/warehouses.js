import { request } from "./client.js";

const path = (id) => `/warehouses/${encodeURIComponent(id)}`;

export const listWarehouses = () => request("GET", "/warehouses");
export const getWarehouse = (id) => request("GET", path(id));
export const createWarehouse = (warehouse) =>
  request("POST", "/warehouses", warehouse);
export const updateWarehouse = (id, warehouse) =>
  request("PUT", path(id), warehouse);
export const deleteWarehouse = (id) => request("DELETE", path(id));

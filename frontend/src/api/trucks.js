import { request } from "./client.js";

const path = (id) => `/trucks/${encodeURIComponent(id)}`;

export const listTrucks = () => request("GET", "/trucks");
export const getTruck = (id) => request("GET", path(id));
export const createTruck = (truck) => request("POST", "/trucks", truck);
export const updateTruck = (id, truck) => request("PUT", path(id), truck);
export const deleteTruck = (id) => request("DELETE", path(id));
export const recommendTrucks = (recommendationRequest) =>
  request("POST", "/trucks/recommend", recommendationRequest);

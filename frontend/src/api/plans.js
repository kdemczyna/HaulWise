import { request } from "./client.js";

export const createPlan = (planRequest) =>
  request("POST", "/plans", planRequest);
export const getPlan = (id) =>
  request("GET", `/plans/${encodeURIComponent(id)}`);

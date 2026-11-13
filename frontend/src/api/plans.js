import { request } from "./client.js";

export const createPlan = (planRequest) =>
  request("POST", "/plans", planRequest);
export const getPlan = (id) =>
  request("GET", `/plans/${encodeURIComponent(id)}`);
/** Sends the trucks of one variant ("OPTIMAL" | "PRIORITY") of a stored plan (T-33). */
export const dispatchPlan = (id, mode) =>
  request("POST", `/plans/${encodeURIComponent(id)}/dispatch`, { mode });

import { request } from "./client.js";

export const getDistances = () => request("GET", "/distances");
export const refreshDistances = () => request("POST", "/distances/refresh");

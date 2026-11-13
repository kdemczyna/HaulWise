import { request } from "./client.js";

export const getSettings = () => request("GET", "/settings");
export const updateSettings = (settings) =>
  request("PUT", "/settings", settings);

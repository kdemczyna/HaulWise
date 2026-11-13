import { ROUTE_COLORS, routeColor } from "./colors.js";

describe("routeColor", () => {
  it("uses the approved colours for the first three trucks", () => {
    expect([0, 1, 2].map(routeColor)).toEqual([
      "#1D5FD1",
      "#7A5AF8",
      "#107569",
    ]);
  });

  it("repeats the palette after the last colour", () => {
    expect(routeColor(ROUTE_COLORS.length)).toBe(ROUTE_COLORS[0]);
    expect(routeColor(ROUTE_COLORS.length + 2)).toBe(ROUTE_COLORS[2]);
  });

  it("has distinct colours", () => {
    expect(new Set(ROUTE_COLORS).size).toBe(ROUTE_COLORS.length);
  });
});

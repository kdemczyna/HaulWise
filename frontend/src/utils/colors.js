/** Truck route colours by the plan's `colorIndex` (approved visual design); repeats after the last one. */
export const ROUTE_COLORS = [
  "#1D5FD1",
  "#7A5AF8",
  "#107569",
  "#C11574",
  "#A15C07",
  "#475467",
];

export function routeColor(colorIndex) {
  const length = ROUTE_COLORS.length;
  return ROUTE_COLORS[((colorIndex % length) + length) % length];
}

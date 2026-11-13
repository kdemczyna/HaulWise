import { Table, Title, VisuallyHidden } from "@mantine/core";
import {
  formatDuration,
  formatKm,
  formatLitres,
  formatPln,
  formatSigned,
} from "../../utils/format.js";
import { TableBox } from "../common/TableBox.jsx";

/** Rows of the comparison; `delta` reads the delivered difference (never recomputed from the totals). */
const ROWS = [
  {
    label: "Total cost",
    value: (variant) => formatPln(variant.totals.costPln),
    delta: (delta) => formatSigned(delta.costPln, formatPln),
  },
  {
    label: "Fuel cost",
    value: (variant) => formatPln(variant.totals.fuelCostPln),
    delta: (delta) => formatSigned(delta.fuelCostPln, formatPln),
  },
  {
    label: "Driver cost",
    value: (variant) => formatPln(variant.totals.driverCostPln),
    delta: (delta) => formatSigned(delta.driverCostPln, formatPln),
  },
  {
    label: "Distance",
    value: (variant) => formatKm(variant.totals.distanceKm),
    delta: (delta) => formatSigned(delta.distanceKm, formatKm),
  },
  {
    label: "Time incl. stops",
    value: (variant) => formatDuration(variant.totals.totalMinutes),
    delta: (delta) => formatSigned(delta.durationMinutes, formatDuration),
  },
  {
    label: "Fuel",
    value: (variant) => formatLitres(variant.totals.litres),
    delta: (delta) => formatSigned(delta.litres, formatLitres),
  },
];

/** Optimal vs priority totals with the delivered `delta` as the Difference column. */
export function PlanComparison({ plan }) {
  const { optimal, priority, delta } = plan;
  const missedDifference = priority.deadlinesMissed - optimal.deadlinesMissed;

  return (
    <section aria-labelledby="comparison-title">
      <Title order={2} size="h4" mb="sm" id="comparison-title">
        Optimal vs priority
      </Title>
      <TableBox label="Optimal vs priority totals" minWidth={420}>
        <Table.Thead>
          <Table.Tr>
            <Table.Th scope="col">
              <VisuallyHidden>Measure</VisuallyHidden>
            </Table.Th>
            <Table.Th scope="col">Optimal</Table.Th>
            <Table.Th scope="col">Priority</Table.Th>
            <Table.Th scope="col">Difference</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody className="hw-numeric">
          {ROWS.map((row) => (
            <Table.Tr key={row.label}>
              <Table.Th scope="row">{row.label}</Table.Th>
              <Table.Td>{row.value(optimal)}</Table.Td>
              <Table.Td>{row.value(priority)}</Table.Td>
              <Table.Td>{row.delta(delta)}</Table.Td>
            </Table.Tr>
          ))}
          <Table.Tr>
            <Table.Th scope="row">Deadlines missed</Table.Th>
            <Table.Td>{optimal.deadlinesMissed}</Table.Td>
            <Table.Td>{priority.deadlinesMissed}</Table.Td>
            <Table.Td>{formatSigned(missedDifference, String)}</Table.Td>
          </Table.Tr>
        </Table.Tbody>
      </TableBox>
    </section>
  );
}

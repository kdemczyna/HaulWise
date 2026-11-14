import { Badge, Button, Group, Table } from "@mantine/core";
import { Link } from "react-router";
import { listPlans } from "../api/plans.js";
import { EmptyState } from "../components/common/EmptyState.jsx";
import { ListView } from "../components/common/ListView.jsx";
import { PageHeader } from "../components/common/PageHeader.jsx";
import { ActionsHeader } from "../components/common/RowActions.jsx";
import { TableBox } from "../components/common/TableBox.jsx";
import { useAsync } from "../hooks/useAsync.js";
import { formatDateTime, formatInteger } from "../utils/format.js";

/**
 * Recent plans (T-34, US-18): what the server's in-memory plan store holds, in the order the
 * API returns it (newest created first). Each row opens the existing result page.
 */
export function RecentPlansPage() {
  const plans = useAsync(listPlans);

  return (
    <>
      <PageHeader
        title="Recent plans"
        description="The plans the server keeps in memory: up to 50, newest first. They are lost when the server restarts."
      />
      <ListView
        result={plans}
        label="plans"
        empty={
          <EmptyState
            title="No plans yet"
            message="Plans you make appear here until the server restarts."
            action={
              <Button component={Link} to="/">
                Plan a run
              </Button>
            }
          />
        }
      >
        {(items) => <PlansTable plans={items} />}
      </ListView>
    </>
  );
}

function PlansTable({ plans }) {
  return (
    <TableBox label="Recent plans" minWidth={640}>
      <Table.Thead>
        <Table.Tr>
          <Table.Th>Depot</Table.Th>
          <Table.Th>Created</Table.Th>
          <Table.Th>Status</Table.Th>
          <Table.Th ta="right">Shipments</Table.Th>
          <Table.Th ta="right">Trucks</Table.Th>
          <ActionsHeader />
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {plans.map((plan) => (
          <Table.Tr key={plan.id}>
            <Table.Td fw={600}>{plan.depotName}</Table.Td>
            <Table.Td className="hw-numeric">
              {formatDateTime(plan.createdAt)}
            </Table.Td>
            <Table.Td>
              <PlanStatus dispatchedAt={plan.dispatchedAt} />
            </Table.Td>
            <Table.Td ta="right" className="hw-numeric">
              {formatInteger(plan.shipmentCount)}
            </Table.Td>
            <Table.Td ta="right" className="hw-numeric">
              {formatInteger(plan.truckCount)}
            </Table.Td>
            <Table.Td>
              <Group justify="flex-end" wrap="nowrap">
                <Button
                  component={Link}
                  to={`/plans/${encodeURIComponent(plan.id)}`}
                  variant="default"
                  size="xs"
                  aria-label={`Open plan from ${plan.depotName}, created ${formatDateTime(plan.createdAt)}`}
                >
                  Open
                </Button>
              </Group>
            </Table.Td>
          </Table.Tr>
        ))}
      </Table.Tbody>
    </TableBox>
  );
}

/** "Sent <time>" in the result page's sent style, or a neutral "Draft" while not dispatched. */
function PlanStatus({ dispatchedAt }) {
  return dispatchedAt ? (
    <Badge
      radius="xl"
      variant="outline"
      color="var(--hw-color-success)"
      tt="none"
    >
      Sent {formatDateTime(dispatchedAt)}
    </Badge>
  ) : (
    <Badge radius="xl" variant="light" color="gray" tt="none">
      Draft
    </Badge>
  );
}

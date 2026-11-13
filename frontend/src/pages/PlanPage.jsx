import { Button } from "@mantine/core";
import { Link, useLocation } from "react-router";
import { listTrucks } from "../api/trucks.js";
import { listWarehouses } from "../api/warehouses.js";
import { EmptyState } from "../components/common/EmptyState.jsx";
import { ListView } from "../components/common/ListView.jsx";
import { PageHeader } from "../components/common/PageHeader.jsx";
import { PlanForm } from "../components/forms/PlanForm.jsx";
import { useAsync } from "../hooks/useAsync.js";

/** Plan a run (US-4). "Edit run" on the result page passes its request as `location.state.request`. */
export function PlanPage() {
  const location = useLocation();
  const previousRequest = location.state?.request ?? null;
  const warehouses = useAsync(listWarehouses);
  const trucks = useAsync(listTrucks);

  return (
    <>
      <PageHeader
        title="Plan a run"
        description="Pick a depot, the shipments and the trucks, then compare the cheapest plan with the one that respects priorities."
      />
      <ListView
        result={warehouses}
        label="warehouses"
        empty={
          <EmptyState
            title="No warehouses yet"
            message="Add a depot and destinations before planning a run."
            action={
              <Button component={Link} to="/warehouses">
                Go to Warehouses
              </Button>
            }
          />
        }
      >
        {(items) => (
          <PlanForm
            key={location.key}
            warehouses={items}
            trucks={trucks}
            initialRequest={previousRequest}
          />
        )}
      </ListView>
    </>
  );
}

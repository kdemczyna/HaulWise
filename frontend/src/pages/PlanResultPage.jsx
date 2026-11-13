import { Button, Stack } from "@mantine/core";
import { useCallback } from "react";
import { Link, useParams } from "react-router";
import { getPlan } from "../api/plans.js";
import { listTrucks } from "../api/trucks.js";
import { EmptyState } from "../components/common/EmptyState.jsx";
import { ErrorState } from "../components/common/ErrorState.jsx";
import { LoadingState } from "../components/common/LoadingState.jsx";
import { PageHeader } from "../components/common/PageHeader.jsx";
import { DeltaBanner } from "../components/plan/DeltaBanner.jsx";
import { PlanComparison } from "../components/plan/PlanComparison.jsx";
import { PlanIssues } from "../components/plan/PlanIssues.jsx";
import { RoutesSection } from "../components/plan/RoutesSection.jsx";
import { RunSummary } from "../components/plan/RunSummary.jsx";
import { useAsync } from "../hooks/useAsync.js";
import { unusedTruckIds } from "../utils/plan.js";

/** Plates of the selected trucks without a route; the id when the truck list is unavailable. */
function unusedPlates(plan, trucks) {
  return unusedTruckIds(plan).map(
    (id) => trucks?.find((truck) => truck.id === id)?.plate ?? `truck ${id}`,
  );
}

function PlanContent({ id }) {
  const loadPlan = useCallback(() => getPlan(id), [id]);
  const plan = useAsync(loadPlan);
  const trucks = useAsync(listTrucks);

  if (plan.error?.status === 404) {
    return (
      <EmptyState
        title="Plan not found"
        message="Plans are kept in memory and lost when the server restarts. Plan the run again."
        action={
          <Button component={Link} to="/">
            Plan a run
          </Button>
        }
      />
    );
  }
  if (plan.error) {
    return (
      <ErrorState
        title="Could not load the plan"
        error={plan.error}
        onRetry={plan.reload}
      />
    );
  }
  if (!plan.data) {
    return <LoadingState label="Loading the plan…" rows={5} />;
  }

  return (
    <Stack gap="lg">
      <RunSummary plan={plan.data} />
      <DeltaBanner plan={plan.data} />
      <PlanComparison plan={plan.data} />
      <RoutesSection
        plan={plan.data}
        unusedPlates={unusedPlates(plan.data, trucks.data)}
      />
      <PlanIssues plan={plan.data} />
    </Stack>
  );
}

/** Result of a planning run (US-5..US-8): cost of priority, comparison, routes, issues. */
export function PlanResultPage() {
  const { id } = useParams();
  return (
    <>
      <PageHeader title="Plan result" />
      <PlanContent key={id} id={id} />
    </>
  );
}

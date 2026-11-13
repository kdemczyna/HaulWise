import { Alert, Button, Group, Paper, Stack, Text } from "@mantine/core";
import { useState } from "react";
import { Link, useNavigate } from "react-router";
import { errorMessage } from "../../api/client.js";
import { createPlan } from "../../api/plans.js";
import { formatDateTime } from "../../utils/format.js";

function plural(count, word) {
  return `${count} ${word}${count === 1 ? "" : "s"}`;
}

/**
 * Depot, departure and counts of the run, with "Edit run" (prefilled form), "Plan again" (same
 * request) and `actions` (e.g. "Send trucks") next to them.
 */
export function RunSummary({ plan, actions }) {
  const navigate = useNavigate();
  const [replanning, setReplanning] = useState(false);
  const [error, setError] = useState(null);
  const { request, depot } = plan;

  const planAgain = async () => {
    setError(null);
    setReplanning(true);
    try {
      const next = await createPlan(request);
      navigate(`/plans/${next.id}`);
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setReplanning(false);
    }
  };

  return (
    <Paper withBorder p="md">
      <Stack gap="sm">
        <Group justify="space-between" wrap="wrap" gap="sm">
          <Text>
            <Text span fw={600}>
              {depot.name}
            </Text>{" "}
            ({depot.city}), departure {formatDateTime(request.departureTime)},{" "}
            {plural(request.shipmentIds.length, "shipment")},{" "}
            {plural(request.truckIds.length, "truck")},{" "}
            {request.returnToDepot ? "return to depot" : "no return to depot"}
          </Text>
          <Group gap="sm">
            {actions}
            <Button
              component={Link}
              to="/"
              state={{ request }}
              variant="default"
            >
              Edit run
            </Button>
            <Button onClick={planAgain} loading={replanning}>
              Plan again
            </Button>
          </Group>
        </Group>
        {error && (
          <Alert color="red" variant="light" role="alert">
            {error}
          </Alert>
        )}
      </Stack>
    </Paper>
  );
}

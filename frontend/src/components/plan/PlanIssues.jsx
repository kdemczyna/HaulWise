import {
  Badge,
  Group,
  Paper,
  SimpleGrid,
  Stack,
  Text,
  Title,
} from "@mantine/core";
import { VARIANT_LABELS, allWarnings } from "../../utils/plan.js";

const SEVERITY_COLORS = { WARNING: "priority", INFO: "gray" };

function Panel({ id, title, children }) {
  return (
    <Paper withBorder p="md" component="section" aria-labelledby={id}>
      <Title order={2} size="h4" mb="sm" id={id}>
        {title}
      </Title>
      {children}
    </Paper>
  );
}

/** Bottom panels: shipments that could not be loaded (with the reason) and all warnings. */
export function PlanIssues({ plan }) {
  const warnings = allWarnings(plan);
  return (
    <SimpleGrid cols={{ base: 1, md: 2 }} spacing="md">
      <Panel id="not-loaded-title" title="Not loaded">
        {plan.unassigned.length === 0 ? (
          <Text c="dimmed">All selected shipments were loaded.</Text>
        ) : (
          <Stack component="ul" gap="xs" className="hw-plain-list">
            {plan.unassigned.map((item) => (
              <li key={item.shipmentId}>
                <Text fw={600} span>
                  {item.reference}
                </Text>
                : {item.message}
              </li>
            ))}
          </Stack>
        )}
      </Panel>
      <Panel id="warnings-title" title="Warnings">
        {warnings.length === 0 ? (
          <Text c="dimmed">No warnings.</Text>
        ) : (
          <Stack component="ul" gap="xs" className="hw-plain-list">
            {warnings.map((warning) => (
              <li key={warning.key}>
                <Group gap="xs" wrap="nowrap" align="flex-start">
                  <Badge
                    color={SEVERITY_COLORS[warning.severity] ?? "gray"}
                    variant="light"
                    radius="xl"
                    className="hw-badge-fixed"
                  >
                    {warning.severity}
                  </Badge>
                  <Text size="sm">
                    {warning.variant && (
                      <Text span fw={600} size="sm">
                        {VARIANT_LABELS[warning.variant]} plan:{" "}
                      </Text>
                    )}
                    {warning.message}
                  </Text>
                </Group>
              </li>
            ))}
          </Stack>
        )}
      </Panel>
    </SimpleGrid>
  );
}

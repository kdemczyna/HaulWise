import { Group, Stack, Text, Title } from "@mantine/core";
import { useDocumentTitle } from "@mantine/hooks";

/**
 * Page title (the page's only h1, focusable so AppLayout can move focus to it after
 * navigation), optional description and action buttons. Also sets the document title.
 */
export function PageHeader({ title, description, actions }) {
  useDocumentTitle(`${title} - HaulWise`);
  return (
    <Group justify="space-between" align="flex-start" mb="lg" wrap="wrap">
      <Stack gap={4}>
        <Title order={1} tabIndex={-1}>
          {title}
        </Title>
        {description && <Text c="dimmed">{description}</Text>}
      </Stack>
      {actions && <Group gap="sm">{actions}</Group>}
    </Group>
  );
}

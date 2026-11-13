import { Button, Group, Stack, Table, Text } from "@mantine/core";
import { useCallback, useState } from "react";
import { Link } from "react-router";
import {
  createShipment,
  deleteShipment,
  listShipments,
  updateShipment,
} from "../api/shipments.js";
import { listWarehouses } from "../api/warehouses.js";
import { ConfirmDeleteModal } from "../components/common/ConfirmDeleteModal.jsx";
import { EditorModal } from "../components/common/EditorModal.jsx";
import { EmptyState } from "../components/common/EmptyState.jsx";
import { ErrorState } from "../components/common/ErrorState.jsx";
import { ListView } from "../components/common/ListView.jsx";
import { PageHeader } from "../components/common/PageHeader.jsx";
import { PinnedTag } from "../components/common/PinnedTag.jsx";
import { PriorityBadge } from "../components/common/PriorityBadge.jsx";
import { ActionsHeader, RowActions } from "../components/common/RowActions.jsx";
import { TableBox } from "../components/common/TableBox.jsx";
import { ShipmentFilters } from "../components/forms/ShipmentFilters.jsx";
import { ShipmentForm } from "../components/forms/ShipmentForm.jsx";
import { useAsync } from "../hooks/useAsync.js";
import { useCrudDialogs } from "../hooks/useCrudDialogs.js";
import { formatDeadline, formatInteger, formatKg } from "../utils/format.js";
import { notifySuccess } from "../utils/notify.js";
import { reloadIfGone } from "../utils/reloadIfGone.js";

const ALL_PRIORITIES = "ALL";

export function ShipmentsPage() {
  const [originFilter, setOriginFilter] = useState("");
  const [priorityFilter, setPriorityFilter] = useState(ALL_PRIORITIES);
  const filtered = originFilter !== "" || priorityFilter !== ALL_PRIORITIES;

  const loadShipments = useCallback(
    () =>
      listShipments({
        priority:
          priorityFilter === ALL_PRIORITIES ? undefined : priorityFilter,
        originId: originFilter === "" ? undefined : Number(originFilter),
      }),
    [originFilter, priorityFilter],
  );
  const shipments = useAsync(loadShipments);
  const warehouses = useAsync(listWarehouses);
  const dialogs = useCrudDialogs();
  const { editor, deletion } = dialogs;

  const save = dialogs.track(async (request) => {
    const saved = editor.item
      ? await reloadIfGone(
          () => updateShipment(editor.item.id, request),
          shipments.reload,
        )
      : await createShipment(request);
    dialogs.closeEditor();
    notifySuccess(`Shipment ${saved.reference} saved`);
    shipments.reload();
  });

  async function confirmDelete() {
    await reloadIfGone(
      () => deleteShipment(deletion.item.id),
      shipments.reload,
    );
    dialogs.closeDelete();
    notifySuccess(`Shipment ${deletion.item.reference} deleted`);
    shipments.reload();
  }

  function clearFilters() {
    setOriginFilter("");
    setPriorityFilter(ALL_PRIORITIES);
  }

  const addButton = (
    <Button onClick={dialogs.openCreate} disabled={!warehouses.data}>
      Add shipment
    </Button>
  );

  return (
    <>
      <PageHeader
        title="Shipments"
        description={describe(shipments, filtered)}
        actions={addButton}
      />
      <Stack gap="md">
        {warehouses.error && (
          <ErrorState
            title="Could not load warehouses"
            error={warehouses.error}
            onRetry={warehouses.reload}
          />
        )}
        <ShipmentFilters
          warehouses={warehouses.data ?? []}
          origin={originFilter}
          onOriginChange={setOriginFilter}
          priority={priorityFilter}
          onPriorityChange={setPriorityFilter}
        />
        <ListView
          result={shipments}
          label="shipments"
          empty={
            filtered ? (
              <EmptyState
                title="No shipments match these filters"
                message="Choose another warehouse or priority."
                action={
                  <Button variant="default" onClick={clearFilters}>
                    Clear filters
                  </Button>
                }
              />
            ) : (
              <EmptyState
                title="No shipments yet"
                message="Add your first shipment to plan a run."
                action={addButton}
              />
            )
          }
        >
          {(items) => (
            <ShipmentsTable
              shipments={items}
              onEdit={dialogs.openEdit}
              onDelete={dialogs.openDelete}
              editDisabled={!warehouses.data}
            />
          )}
        </ListView>
      </Stack>

      <EditorModal
        opened={editor.opened}
        onClose={dialogs.closeEditor}
        title={
          editor.item
            ? `Edit shipment ${editor.item.reference}`
            : "Add shipment"
        }
        busy={dialogs.busy}
      >
        {warehouses.data?.length === 0 ? (
          <NoWarehouses />
        ) : (
          <ShipmentForm
            key={editor.item?.id ?? "new"}
            shipment={editor.item}
            warehouses={warehouses.data ?? []}
            defaultOriginId={originFilter}
            onSave={save}
            onCancel={dialogs.closeEditor}
          />
        )}
      </EditorModal>
      <ConfirmDeleteModal
        opened={deletion.opened}
        onClose={dialogs.closeDelete}
        onConfirm={confirmDelete}
        itemType="shipment"
        itemName={deletion.item?.reference}
      />
    </>
  );
}

/** Count of the listed shipments; the generic text while a (re)load runs. */
function describe({ data: shipments, loading }, filtered) {
  if (!shipments || loading) {
    return "Orders waiting to be delivered, with their priority, deadline and pin-first flag.";
  }
  const one = shipments.length === 1;
  const count = `${shipments.length} ${one ? "shipment" : "shipments"}`;
  if (filtered) {
    return `${count} ${one ? "matches" : "match"} the filters`;
  }
  return `${count} waiting to be planned`;
}

/** Shown instead of the form while there is no warehouse to ship from or to. */
function NoWarehouses() {
  return (
    <Stack gap="sm" align="flex-start">
      <Text>
        Add a warehouse first: a shipment needs an origin and a destination.
      </Text>
      <Button component={Link} to="/warehouses">
        Go to Warehouses
      </Button>
    </Stack>
  );
}

function ShipmentsTable({ shipments, onEdit, onDelete, editDisabled }) {
  return (
    <TableBox label="Shipments" minWidth={960}>
      <Table.Thead>
        <Table.Tr>
          <Table.Th>Reference</Table.Th>
          <Table.Th>Route</Table.Th>
          <Table.Th ta="right">Pallets</Table.Th>
          <Table.Th ta="right">Weight</Table.Th>
          <Table.Th>Priority</Table.Th>
          <Table.Th>Deadline</Table.Th>
          <ActionsHeader />
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {shipments.map((shipment) => (
          <Table.Tr key={shipment.id}>
            <Table.Td fw={600}>{shipment.reference}</Table.Td>
            <Table.Td>
              {shipment.origin.name} → {shipment.destination.city}
            </Table.Td>
            <Table.Td ta="right" className="hw-numeric">
              {formatInteger(shipment.pallets)}
            </Table.Td>
            <Table.Td ta="right" className="hw-numeric">
              {formatKg(shipment.weightKg)}
            </Table.Td>
            <Table.Td className="hw-nowrap">
              <Group gap={6} wrap="nowrap">
                <PriorityBadge priority={shipment.priority} />
                {shipment.pinnedFirst && <PinnedTag />}
              </Group>
            </Table.Td>
            <Table.Td className="hw-nowrap">
              {formatDeadline(shipment.deadline)}
            </Table.Td>
            <RowActions
              itemName={shipment.reference}
              onEdit={() => onEdit(shipment)}
              editDisabled={editDisabled}
              onDelete={() => onDelete(shipment)}
            />
          </Table.Tr>
        ))}
      </Table.Tbody>
    </TableBox>
  );
}

import { Alert, Badge, Button, Group, Stack, Table, Text } from "@mantine/core";
import { useState } from "react";
import { Link } from "react-router";
import { errorMessage } from "../api/client.js";
import {
  createTruck,
  deleteTruck,
  listTrucks,
  setTruckAvailable,
  updateTruck,
} from "../api/trucks.js";
import { listWarehouses } from "../api/warehouses.js";
import { ConfirmDeleteModal } from "../components/common/ConfirmDeleteModal.jsx";
import { EditorModal } from "../components/common/EditorModal.jsx";
import { EmptyState } from "../components/common/EmptyState.jsx";
import { ErrorState } from "../components/common/ErrorState.jsx";
import { ListView } from "../components/common/ListView.jsx";
import { PageHeader } from "../components/common/PageHeader.jsx";
import { ActionsHeader, RowActions } from "../components/common/RowActions.jsx";
import { TableBox } from "../components/common/TableBox.jsx";
import { TruckForm } from "../components/forms/TruckForm.jsx";
import { useAsync } from "../hooks/useAsync.js";
import { useCrudDialogs } from "../hooks/useCrudDialogs.js";
import { formatInteger, formatKg } from "../utils/format.js";
import { notifySuccess } from "../utils/notify.js";
import { reloadIfGone } from "../utils/reloadIfGone.js";

const decimalFormat = new Intl.NumberFormat("en-GB", {
  maximumFractionDigits: 2,
});

export function TrucksPage() {
  const trucks = useAsync(listTrucks);
  const warehouses = useAsync(listWarehouses);
  const dialogs = useCrudDialogs();
  const { editor, deletion } = dialogs;
  const [toggling, setToggling] = useState(null);
  const [toggleError, setToggleError] = useState(null);

  const save = dialogs.track(async (request) => {
    const saved = editor.item
      ? await reloadIfGone(
          () => updateTruck(editor.item.id, request),
          trucks.reload,
        )
      : await createTruck(request);
    dialogs.closeEditor();
    notifySuccess(`Truck ${saved.plate} saved`);
    trucks.reload();
  });

  async function confirmDelete() {
    await reloadIfGone(() => deleteTruck(deletion.item.id), trucks.reload);
    dialogs.closeDelete();
    notifySuccess(`Truck ${deletion.item.plate} deleted`);
    trucks.reload();
  }

  async function toggleAvailable(truck) {
    setToggling(truck.id);
    setToggleError(null);
    try {
      const saved = await reloadIfGone(
        () => setTruckAvailable(truck.id, !truck.available),
        trucks.reload,
      );
      notifySuccess(
        `Truck ${saved.plate} is ${saved.available ? "available" : "on route"}`,
      );
      trucks.reload();
    } catch (error) {
      setToggleError(`${truck.plate}: ${errorMessage(error)}`);
    } finally {
      setToggling(null);
    }
  }

  const addButton = (
    <Button onClick={dialogs.openCreate} disabled={!warehouses.data}>
      Add truck
    </Button>
  );

  return (
    <>
      <PageHeader
        title="Trucks"
        description="Your fleet: home depot, availability, capacity, payload, fuel consumption and driver cost."
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
        {toggleError && (
          <Alert
            color="red"
            variant="light"
            role="alert"
            title="Could not change the status"
            withCloseButton
            closeButtonLabel="Dismiss"
            onClose={() => setToggleError(null)}
          >
            {toggleError}
          </Alert>
        )}
        <ListView
          result={trucks}
          label="trucks"
          empty={
            <EmptyState
              title="No trucks yet"
              message="Add your first truck to plan runs."
              action={addButton}
            />
          }
        >
          {(items) => (
            <TrucksTable
              trucks={items}
              toggling={toggling}
              onToggle={toggleAvailable}
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
        title={editor.item ? `Edit ${editor.item.plate}` : "Add truck"}
        busy={dialogs.busy}
      >
        {warehouses.data?.length === 0 ? (
          <NoWarehouses />
        ) : (
          <TruckForm
            key={editor.item?.id ?? "new"}
            truck={editor.item}
            warehouses={warehouses.data ?? []}
            onSave={save}
            onCancel={dialogs.closeEditor}
          />
        )}
      </EditorModal>
      <ConfirmDeleteModal
        opened={deletion.opened}
        onClose={dialogs.closeDelete}
        onConfirm={confirmDelete}
        itemType="truck"
        itemName={deletion.item?.plate}
      />
    </>
  );
}

function TrucksTable({
  trucks,
  toggling,
  onToggle,
  onEdit,
  onDelete,
  editDisabled,
}) {
  return (
    <TableBox label="Trucks" minWidth={1020}>
      <Table.Thead>
        <Table.Tr>
          <Table.Th>Plate</Table.Th>
          <Table.Th>Home depot</Table.Th>
          <Table.Th>Status</Table.Th>
          <Table.Th ta="right">Capacity</Table.Th>
          <Table.Th ta="right">Payload</Table.Th>
          <Table.Th ta="right">Consumption (L/100 km)</Table.Th>
          <Table.Th ta="right">Driver cost</Table.Th>
          <ActionsHeader />
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {trucks.map((truck) => (
          <Table.Tr key={truck.id}>
            <Table.Td fw={600}>{truck.plate}</Table.Td>
            <Table.Td>{truck.homeWarehouse.name}</Table.Td>
            <Table.Td miw={240}>
              <Group gap="xs" wrap="nowrap">
                <AvailabilityBadge available={truck.available} />
                <Button
                  variant="subtle"
                  size="xs"
                  loading={toggling === truck.id}
                  disabled={toggling !== null && toggling !== truck.id}
                  onClick={() => onToggle(truck)}
                  aria-label={`Mark ${truck.plate} ${truck.available ? "on route" : "available"}`}
                >
                  {truck.available ? "Mark on route" : "Mark available"}
                </Button>
              </Group>
            </Table.Td>
            <Table.Td ta="right" className="hw-numeric">
              {formatInteger(truck.capacityPallets)} pallets
            </Table.Td>
            <Table.Td ta="right" className="hw-numeric">
              {formatKg(truck.payloadKg)}
            </Table.Td>
            <Table.Td ta="right" className="hw-numeric">
              {decimalFormat.format(truck.baseConsumptionLPer100Km)} +{" "}
              {decimalFormat.format(truck.extraConsumptionFullLoadLPer100Km)} at
              full load
            </Table.Td>
            <Table.Td ta="right" className="hw-numeric">
              {decimalFormat.format(truck.driverCostPlnPerHour)} PLN/h
            </Table.Td>
            <RowActions
              itemName={truck.plate}
              onEdit={() => onEdit(truck)}
              onDelete={() => onDelete(truck)}
              editDisabled={editDisabled}
            />
          </Table.Tr>
        ))}
      </Table.Tbody>
    </TableBox>
  );
}

function AvailabilityBadge({ available }) {
  return available ? (
    <Badge radius="xl" variant="outline" color="var(--hw-color-success)">
      Available
    </Badge>
  ) : (
    <Badge radius="xl" variant="light" color="priority">
      On route
    </Badge>
  );
}

/** Shown instead of the form while there is no warehouse to base a truck at. */
function NoWarehouses() {
  return (
    <Stack gap="sm" align="flex-start">
      <Text>Add a warehouse first: every truck has a home depot.</Text>
      <Button component={Link} to="/warehouses">
        Go to Warehouses
      </Button>
    </Stack>
  );
}

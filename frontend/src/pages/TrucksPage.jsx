import { Button, Table } from "@mantine/core";
import {
  createTruck,
  deleteTruck,
  listTrucks,
  updateTruck,
} from "../api/trucks.js";
import { ConfirmDeleteModal } from "../components/common/ConfirmDeleteModal.jsx";
import { EditorModal } from "../components/common/EditorModal.jsx";
import { EmptyState } from "../components/common/EmptyState.jsx";
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
  const dialogs = useCrudDialogs();
  const { editor, deletion } = dialogs;

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

  const addButton = <Button onClick={dialogs.openCreate}>Add truck</Button>;

  return (
    <>
      <PageHeader
        title="Trucks"
        description="Your fleet: capacity, payload, fuel consumption and driver cost."
        actions={addButton}
      />
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
          <TableBox label="Trucks" minWidth={760}>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Plate</Table.Th>
                <Table.Th ta="right">Capacity</Table.Th>
                <Table.Th ta="right">Payload</Table.Th>
                <Table.Th ta="right">Consumption (L/100 km)</Table.Th>
                <Table.Th ta="right">Driver cost</Table.Th>
                <ActionsHeader />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {items.map((truck) => (
                <Table.Tr key={truck.id}>
                  <Table.Td fw={600}>{truck.plate}</Table.Td>
                  <Table.Td ta="right" className="hw-numeric">
                    {formatInteger(truck.capacityPallets)} pallets
                  </Table.Td>
                  <Table.Td ta="right" className="hw-numeric">
                    {formatKg(truck.payloadKg)}
                  </Table.Td>
                  <Table.Td ta="right" className="hw-numeric">
                    {decimalFormat.format(truck.baseConsumptionLPer100Km)} +{" "}
                    {decimalFormat.format(
                      truck.extraConsumptionFullLoadLPer100Km,
                    )}{" "}
                    at full load
                  </Table.Td>
                  <Table.Td ta="right" className="hw-numeric">
                    {decimalFormat.format(truck.driverCostPlnPerHour)} PLN/h
                  </Table.Td>
                  <RowActions
                    itemName={truck.plate}
                    onEdit={() => dialogs.openEdit(truck)}
                    onDelete={() => dialogs.openDelete(truck)}
                  />
                </Table.Tr>
              ))}
            </Table.Tbody>
          </TableBox>
        )}
      </ListView>

      <EditorModal
        opened={editor.opened}
        onClose={dialogs.closeEditor}
        title={editor.item ? `Edit ${editor.item.plate}` : "Add truck"}
        busy={dialogs.busy}
      >
        <TruckForm
          key={editor.item?.id ?? "new"}
          truck={editor.item}
          onSave={save}
          onCancel={dialogs.closeEditor}
        />
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

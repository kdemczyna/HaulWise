import { Button, Table } from "@mantine/core";
import {
  createWarehouse,
  deleteWarehouse,
  listWarehouses,
  updateWarehouse,
} from "../api/warehouses.js";
import { ConfirmDeleteModal } from "../components/common/ConfirmDeleteModal.jsx";
import { EditorModal } from "../components/common/EditorModal.jsx";
import { EmptyState } from "../components/common/EmptyState.jsx";
import { ListView } from "../components/common/ListView.jsx";
import { PageHeader } from "../components/common/PageHeader.jsx";
import { ActionsHeader, RowActions } from "../components/common/RowActions.jsx";
import { TableBox } from "../components/common/TableBox.jsx";
import { WarehouseForm } from "../components/forms/WarehouseForm.jsx";
import { useAsync } from "../hooks/useAsync.js";
import { useCrudDialogs } from "../hooks/useCrudDialogs.js";
import { notifySuccess } from "../utils/notify.js";
import { reloadIfGone } from "../utils/reloadIfGone.js";

const coordinateFormat = new Intl.NumberFormat("en-GB", {
  maximumFractionDigits: 4,
});

export function WarehousesPage() {
  const warehouses = useAsync(listWarehouses);
  const dialogs = useCrudDialogs();
  const { editor, deletion } = dialogs;

  const save = dialogs.track(async (request) => {
    const saved = editor.item
      ? await reloadIfGone(
          () => updateWarehouse(editor.item.id, request),
          warehouses.reload,
        )
      : await createWarehouse(request);
    dialogs.closeEditor();
    notifySuccess(`Warehouse ${saved.name} saved`);
    warehouses.reload();
  });

  async function confirmDelete() {
    await reloadIfGone(
      () => deleteWarehouse(deletion.item.id),
      warehouses.reload,
    );
    dialogs.closeDelete();
    notifySuccess(`Warehouse ${deletion.item.name} deleted`);
    warehouses.reload();
  }

  const addButton = <Button onClick={dialogs.openCreate}>Add warehouse</Button>;

  return (
    <>
      <PageHeader
        title="Warehouses"
        description="Depots and delivery points used by shipments and routes."
        actions={addButton}
      />
      <ListView
        result={warehouses}
        label="warehouses"
        empty={
          <EmptyState
            title="No warehouses yet"
            message="Add your first warehouse to start creating shipments."
            action={addButton}
          />
        }
      >
        {(items) => (
          <TableBox label="Warehouses" minWidth={640}>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Name</Table.Th>
                <Table.Th>City</Table.Th>
                <Table.Th>Coordinates</Table.Th>
                <ActionsHeader />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {items.map((warehouse) => (
                <Table.Tr key={warehouse.id}>
                  <Table.Td fw={600}>{warehouse.name}</Table.Td>
                  <Table.Td>{warehouse.city}</Table.Td>
                  <Table.Td className="hw-numeric">
                    {coordinateFormat.format(warehouse.lat)},{" "}
                    {coordinateFormat.format(warehouse.lon)}
                  </Table.Td>
                  <RowActions
                    itemName={warehouse.name}
                    onEdit={() => dialogs.openEdit(warehouse)}
                    onDelete={() => dialogs.openDelete(warehouse)}
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
        title={editor.item ? `Edit ${editor.item.name}` : "Add warehouse"}
        busy={dialogs.busy}
      >
        <WarehouseForm
          key={editor.item?.id ?? "new"}
          warehouse={editor.item}
          onSave={save}
          onCancel={dialogs.closeEditor}
        />
      </EditorModal>
      <ConfirmDeleteModal
        opened={deletion.opened}
        onClose={dialogs.closeDelete}
        onConfirm={confirmDelete}
        itemType="warehouse"
        itemName={deletion.item?.name}
      />
    </>
  );
}

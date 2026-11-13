import { screen, waitFor, within } from "@testing-library/react";
import { ApiError } from "../api/client.js";
import {
  createWarehouse,
  deleteWarehouse,
  listWarehouses,
  updateWarehouse,
} from "../api/warehouses.js";
import { renderWithProviders } from "../test/renderWithProviders.jsx";
import { WarehousesPage } from "./WarehousesPage.jsx";

vi.mock("../api/warehouses.js");

const WAW = {
  id: 1,
  name: "WAW Central",
  city: "Warsaw",
  lat: 52.2297,
  lon: 21.0122,
};
const GDN = {
  id: 3,
  name: "GDN Port",
  city: "Gdansk",
  lat: 54.352,
  lon: 18.6466,
};

function renderPage() {
  return renderWithProviders(<WarehousesPage />);
}

function dialog(name) {
  return within(screen.getByRole("dialog", { name }));
}

describe("WarehousesPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    listWarehouses.mockResolvedValue([GDN, WAW]);
  });

  it("lists the warehouses in API order", async () => {
    renderPage();

    const table = await screen.findByRole("table", { name: "Warehouses" });
    const rows = within(table).getAllByRole("row").slice(1);
    expect(
      rows.map((row) => within(row).getAllByRole("cell")[0].textContent),
    ).toEqual(["GDN Port", "WAW Central"]);
    expect(within(rows[1]).getByText("52.2297, 21.0122")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Edit WAW Central" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Delete WAW Central" }),
    ).toBeInTheDocument();
  });

  it("shows a loading state first", () => {
    listWarehouses.mockReturnValue(new Promise(() => {}));
    renderPage();

    expect(screen.getByRole("status")).toHaveTextContent("Loading warehouses…");
  });

  it("shows an empty state with a call to action", async () => {
    listWarehouses.mockResolvedValue([]);
    renderPage();

    expect(
      await screen.findByRole("heading", { name: "No warehouses yet" }),
    ).toBeInTheDocument();
    expect(
      screen.getAllByRole("button", { name: "Add warehouse" }),
    ).toHaveLength(2);
  });

  it("shows a load error and retries", async () => {
    listWarehouses
      .mockRejectedValueOnce(
        new ApiError({ status: 0, code: "NETWORK", detail: "Server down." }),
      )
      .mockResolvedValueOnce([WAW]);
    const { user } = renderPage();

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Could not load warehouses");
    expect(alert).toHaveTextContent("Server down.");

    await user.click(screen.getByRole("button", { name: "Retry" }));

    expect(await screen.findByText("WAW Central")).toBeInTheDocument();
    expect(listWarehouses).toHaveBeenCalledTimes(2);
  });

  it("creates a warehouse, confirms it and reloads the list", async () => {
    createWarehouse.mockResolvedValue({
      id: 10,
      name: "RZE East",
      city: "Rzeszow",
      lat: 50.04,
      lon: 22.0,
    });
    const { user } = renderPage();
    await screen.findByText("WAW Central");

    await user.click(screen.getByRole("button", { name: "Add warehouse" }));
    const form = dialog("Add warehouse");
    expect(form.getByRole("textbox", { name: /Name/ })).toHaveFocus();
    await user.type(form.getByRole("textbox", { name: /Name/ }), "  RZE East ");
    await user.type(form.getByRole("textbox", { name: /City/ }), "Rzeszow");
    await user.type(form.getByRole("textbox", { name: /Latitude/ }), "50.04");
    await user.type(form.getByRole("textbox", { name: /Longitude/ }), "22");
    await user.click(form.getByRole("button", { name: "Save warehouse" }));

    expect(createWarehouse).toHaveBeenCalledWith({
      name: "RZE East",
      city: "Rzeszow",
      lat: 50.04,
      lon: 22,
    });
    expect(
      await screen.findByText("Warehouse RZE East saved"),
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
    expect(listWarehouses).toHaveBeenCalledTimes(2);
  });

  it("validates on the client before sending", async () => {
    const { user } = renderPage();
    await screen.findByText("WAW Central");

    await user.click(screen.getByRole("button", { name: "Add warehouse" }));
    const form = dialog("Add warehouse");
    await user.type(form.getByRole("textbox", { name: /Name/ }), "X");
    await user.type(form.getByRole("textbox", { name: /Latitude/ }), "91");
    await user.click(form.getByRole("button", { name: "Save warehouse" }));

    expect(
      form.getByRole("textbox", { name: /Name/ }),
    ).toHaveAccessibleDescription(/Name must be 2 to 60 characters/);
    expect(
      form.getByRole("textbox", { name: /City/ }),
    ).toHaveAccessibleDescription("City is required");
    expect(
      form.getByRole("textbox", { name: /Latitude/ }),
    ).toHaveAccessibleDescription(/Latitude must be between -90 and 90/);
    expect(createWarehouse).not.toHaveBeenCalled();
  });

  it("disables the save button while saving", async () => {
    createWarehouse.mockReturnValue(new Promise(() => {}));
    const { user } = renderPage();
    await screen.findByText("WAW Central");

    await user.click(screen.getByRole("button", { name: "Add warehouse" }));
    const form = dialog("Add warehouse");
    await user.type(form.getByRole("textbox", { name: /Name/ }), "RZE East");
    await user.type(form.getByRole("textbox", { name: /City/ }), "Rzeszow");
    await user.type(form.getByRole("textbox", { name: /Latitude/ }), "50");
    await user.type(form.getByRole("textbox", { name: /Longitude/ }), "22");
    await user.click(form.getByRole("button", { name: "Save warehouse" }));

    expect(form.getByRole("button", { name: "Save warehouse" })).toBeDisabled();
    expect(form.getByRole("button", { name: "Cancel" })).toBeDisabled();
  });

  it("shows server field errors on the matching field", async () => {
    createWarehouse.mockRejectedValue(
      new ApiError({
        status: 400,
        code: "VALIDATION_FAILED",
        detail: "Request has 1 invalid field.",
        fieldErrors: { city: "size must be between 2 and 60" },
      }),
    );
    const { user } = renderPage();
    await screen.findByText("WAW Central");

    await user.click(screen.getByRole("button", { name: "Add warehouse" }));
    const form = dialog("Add warehouse");
    await user.type(form.getByRole("textbox", { name: /Name/ }), "RZE East");
    await user.type(form.getByRole("textbox", { name: /City/ }), "Rzeszow");
    await user.type(form.getByRole("textbox", { name: /Latitude/ }), "50");
    await user.type(form.getByRole("textbox", { name: /Longitude/ }), "22");
    await user.click(form.getByRole("button", { name: "Save warehouse" }));

    await waitFor(() =>
      expect(
        form.getByRole("textbox", { name: /City/ }),
      ).toHaveAccessibleDescription("size must be between 2 and 60"),
    );
    expect(form.getByRole("alert")).toHaveTextContent(
      "The warehouse could not be saved. Fix the highlighted fields.",
    );
  });

  it("shows a duplicate name conflict in the form", async () => {
    updateWarehouse.mockRejectedValue(
      new ApiError({
        status: 409,
        code: "CONFLICT",
        detail: "A warehouse named 'GDN Port' already exists.",
      }),
    );
    const { user } = renderPage();

    await user.click(
      await screen.findByRole("button", { name: "Edit WAW Central" }),
    );
    const form = dialog("Edit WAW Central");
    const name = form.getByRole("textbox", { name: /Name/ });
    expect(name).toHaveValue("WAW Central");
    await user.clear(name);
    await user.type(name, "GDN Port");
    await user.click(form.getByRole("button", { name: "Save warehouse" }));

    expect(await form.findByRole("alert")).toHaveTextContent(
      "A warehouse named 'GDN Port' already exists.",
    );
    expect(updateWarehouse).toHaveBeenCalledWith(1, {
      name: "GDN Port",
      city: "Warsaw",
      lat: 52.2297,
      lon: 21.0122,
    });
  });

  it("asks before deleting and shows the in-use conflict", async () => {
    deleteWarehouse.mockRejectedValue(
      new ApiError({
        status: 409,
        code: "CONFLICT",
        detail: "Warehouse 'GDN Port' is used by 3 shipments.",
      }),
    );
    const { user } = renderPage();

    await user.click(
      await screen.findByRole("button", { name: "Delete GDN Port" }),
    );
    const confirm = dialog("Delete GDN Port?");
    expect(confirm.getByText("GDN Port")).toBeInTheDocument();
    expect(confirm.getByRole("button", { name: "Cancel" })).toHaveFocus();

    await user.click(confirm.getByRole("button", { name: "Delete" }));

    expect(await confirm.findByRole("alert")).toHaveTextContent(
      "Warehouse 'GDN Port' is used by 3 shipments.",
    );
    expect(deleteWarehouse).toHaveBeenCalledWith(3);
    expect(
      screen.getByRole("dialog", { name: "Delete GDN Port?" }),
    ).toBeInTheDocument();
  });

  it("deletes after confirmation and reloads", async () => {
    deleteWarehouse.mockResolvedValue(null);
    const { user } = renderPage();

    await user.click(
      await screen.findByRole("button", { name: "Delete GDN Port" }),
    );
    await user.click(
      dialog("Delete GDN Port?").getByRole("button", { name: "Delete" }),
    );

    expect(
      await screen.findByText("Warehouse GDN Port deleted"),
    ).toBeInTheDocument();
    expect(listWarehouses).toHaveBeenCalledTimes(2);
  });

  it("cannot be dismissed while saving", async () => {
    createWarehouse.mockReturnValue(new Promise(() => {}));
    const { user } = renderPage();
    await screen.findByText("WAW Central");

    await user.click(screen.getByRole("button", { name: "Add warehouse" }));
    const form = dialog("Add warehouse");
    expect(
      form.getByRole("button", { name: "Close dialog" }),
    ).toBeInTheDocument();
    await user.type(form.getByRole("textbox", { name: /Name/ }), "RZE East");
    await user.type(form.getByRole("textbox", { name: /City/ }), "Rzeszow");
    await user.type(form.getByRole("textbox", { name: /Latitude/ }), "50");
    await user.type(form.getByRole("textbox", { name: /Longitude/ }), "22");
    await user.click(form.getByRole("button", { name: "Save warehouse" }));
    await user.keyboard("{Escape}");

    expect(
      screen.getByRole("dialog", { name: "Add warehouse" }),
    ).toBeInTheDocument();
    expect(
      form.queryByRole("button", { name: "Close dialog" }),
    ).not.toBeInTheDocument();
  });

  it("reloads the list when the edited warehouse no longer exists", async () => {
    updateWarehouse.mockRejectedValue(
      new ApiError({
        status: 404,
        code: "NOT_FOUND",
        detail: "Warehouse 1 not found.",
      }),
    );
    const { user } = renderPage();

    await user.click(
      await screen.findByRole("button", { name: "Edit WAW Central" }),
    );
    const form = dialog("Edit WAW Central");
    await user.click(form.getByRole("button", { name: "Save warehouse" }));

    expect(await form.findByRole("alert")).toHaveTextContent(
      "Warehouse 1 not found.",
    );
    expect(listWarehouses).toHaveBeenCalledTimes(2);
  });

  it("does not delete when cancelled", async () => {
    const { user } = renderPage();

    await user.click(
      await screen.findByRole("button", { name: "Delete GDN Port" }),
    );
    await user.click(
      dialog("Delete GDN Port?").getByRole("button", { name: "Cancel" }),
    );

    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
    expect(deleteWarehouse).not.toHaveBeenCalled();
  });
});

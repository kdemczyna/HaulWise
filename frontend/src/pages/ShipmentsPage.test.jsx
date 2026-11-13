import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { ApiError } from "../api/client.js";
import {
  createShipment,
  deleteShipment,
  listShipments,
  updateShipment,
} from "../api/shipments.js";
import { listWarehouses } from "../api/warehouses.js";
import { renderWithProviders } from "../test/renderWithProviders.jsx";
import { ShipmentsPage } from "./ShipmentsPage.jsx";

vi.mock("../api/shipments.js");
vi.mock("../api/warehouses.js");

const WAW = { id: 1, name: "WAW Central", city: "Warsaw" };
const GDN = { id: 3, name: "GDN Port", city: "Gdansk" };
const LUB = { id: 8, name: "LUB East", city: "Lublin" };

const URGENT_LUBLIN = {
  id: 1,
  reference: "PO-1041",
  origin: WAW,
  destination: LUB,
  pallets: 6,
  weightKg: 3600,
  priority: "URGENT",
  deadline: "2026-11-13T20:00:00",
  pinnedFirst: false,
};
const PINNED_GDANSK = {
  id: 2,
  reference: "PO-1042",
  origin: WAW,
  destination: GDN,
  pallets: 10,
  weightKg: 6500,
  priority: "HIGH",
  deadline: null,
  pinnedFirst: true,
};

function dialog(name) {
  return within(screen.getByRole("dialog", { name }));
}

function rowOf(reference) {
  return screen.getByRole("row", { name: new RegExp(reference) });
}

async function openAddForm(user) {
  await screen.findByText("PO-1041");
  await user.click(screen.getByRole("button", { name: "Add shipment" }));
  return dialog("Add shipment");
}

async function fillNewShipment(user, form) {
  await user.type(form.getByRole("textbox", { name: /Reference/ }), "PO-1100");
  await user.selectOptions(
    form.getByRole("combobox", { name: /From warehouse/ }),
    "WAW Central",
  );
  await user.selectOptions(
    form.getByRole("combobox", { name: /To warehouse/ }),
    "GDN Port",
  );
  await user.type(form.getByRole("textbox", { name: /Pallets/ }), "8");
  await user.type(form.getByRole("textbox", { name: /Weight/ }), "4200");
}

describe("ShipmentsPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    listShipments.mockResolvedValue([URGENT_LUBLIN, PINNED_GDANSK]);
    listWarehouses.mockResolvedValue([GDN, LUB, WAW]);
  });

  it("lists shipments with route, load, priority, pin and deadline", async () => {
    renderWithProviders(<ShipmentsPage />);

    await screen.findByRole("table", { name: "Shipments" });
    const urgent = within(rowOf("PO-1041"));
    expect(urgent.getByText("WAW Central → Lublin")).toBeInTheDocument();
    expect(urgent.getByText(/^3\s600\skg$/)).toBeInTheDocument();
    expect(urgent.getByText("Urgent")).toBeInTheDocument();
    expect(urgent.getByText("13 Nov, 20:00")).toBeInTheDocument();
    expect(urgent.queryByText("Pinned first")).not.toBeInTheDocument();

    const pinned = within(rowOf("PO-1042"));
    expect(pinned.getByText("High")).toBeInTheDocument();
    expect(pinned.getByText("Pinned first")).toBeInTheDocument();
    expect(pinned.getByText("-")).toBeInTheDocument();
    expect(
      pinned.getByRole("button", { name: "Edit PO-1042" }),
    ).toBeInTheDocument();
    expect(
      pinned.getByRole("button", { name: "Delete PO-1042" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("2 shipments waiting to be planned"),
    ).toBeInTheDocument();
    expect(listShipments).toHaveBeenCalledWith({
      priority: undefined,
      originId: undefined,
    });
  });

  it("shows an empty state", async () => {
    listShipments.mockResolvedValue([]);
    renderWithProviders(<ShipmentsPage />);

    expect(
      await screen.findByRole("heading", { name: "No shipments yet" }),
    ).toBeInTheDocument();
  });

  it("shows a load error and retries", async () => {
    listShipments
      .mockRejectedValueOnce(
        new ApiError({ status: 0, code: "NETWORK", detail: "Server down." }),
      )
      .mockResolvedValueOnce([URGENT_LUBLIN]);
    const { user } = renderWithProviders(<ShipmentsPage />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load shipments",
    );
    await user.click(screen.getByRole("button", { name: "Retry" }));

    expect(await screen.findByText("PO-1041")).toBeInTheDocument();
  });

  it("filters by origin warehouse and priority", async () => {
    const { user } = renderWithProviders(<ShipmentsPage />);
    await screen.findByText("PO-1041");
    const filters = screen.getByRole("group", { name: "Priority" });

    await user.selectOptions(
      screen.getByRole("combobox", { name: "From warehouse" }),
      "WAW Central",
    );
    await waitFor(() =>
      expect(listShipments).toHaveBeenLastCalledWith({
        priority: undefined,
        originId: 1,
      }),
    );

    listShipments.mockResolvedValue([]);
    await user.click(within(filters).getByRole("radio", { name: "Urgent" }));
    await waitFor(() =>
      expect(listShipments).toHaveBeenLastCalledWith({
        priority: "URGENT",
        originId: 1,
      }),
    );
    expect(
      await screen.findByRole("heading", {
        name: "No shipments match these filters",
      }),
    ).toBeInTheDocument();

    await user.click(within(filters).getByRole("radio", { name: "All" }));
    await waitFor(() =>
      expect(listShipments).toHaveBeenLastCalledWith({
        priority: undefined,
        originId: 1,
      }),
    );
  });

  it("creates a shipment with a deadline sent without offset", async () => {
    createShipment.mockResolvedValue({
      ...PINNED_GDANSK,
      id: 20,
      reference: "PO-1100",
    });
    const { user } = renderWithProviders(<ShipmentsPage />);
    const form = await openAddForm(user);

    await fillNewShipment(user, form);
    await user.click(form.getByRole("radio", { name: "High" }));
    await user.click(
      form.getByRole("checkbox", { name: /Deliver this stop first/ }),
    );
    fireEvent.change(form.getByLabelText(/Deadline/), {
      target: { value: "2026-11-14T12:00" },
    });
    await user.click(form.getByRole("button", { name: "Save shipment" }));

    expect(createShipment).toHaveBeenCalledWith({
      reference: "PO-1100",
      originId: 1,
      destinationId: 3,
      pallets: 8,
      weightKg: 4200,
      priority: "HIGH",
      pinnedFirst: true,
      deadline: "2026-11-14T12:00:00",
    });
    expect(
      await screen.findByText("Shipment PO-1100 saved"),
    ).toBeInTheDocument();
    expect(listShipments).toHaveBeenCalledTimes(2);
  });

  it("sends no deadline when the field is empty", async () => {
    createShipment.mockResolvedValue({ ...URGENT_LUBLIN, id: 21 });
    const { user } = renderWithProviders(<ShipmentsPage />);
    const form = await openAddForm(user);

    await fillNewShipment(user, form);
    await user.click(form.getByRole("button", { name: "Save shipment" }));

    expect(createShipment).toHaveBeenCalledWith(
      expect.objectContaining({
        priority: "NORMAL",
        pinnedFirst: false,
        deadline: null,
      }),
    );
  });

  it("rejects the same origin and destination on the client", async () => {
    const { user } = renderWithProviders(<ShipmentsPage />);
    const form = await openAddForm(user);

    await fillNewShipment(user, form);
    await user.selectOptions(
      form.getByRole("combobox", { name: /To warehouse/ }),
      "WAW Central",
    );
    await user.click(form.getByRole("button", { name: "Save shipment" }));

    expect(
      form.getByRole("combobox", { name: /To warehouse/ }),
    ).toHaveAccessibleDescription("Must differ from the origin warehouse");
    expect(createShipment).not.toHaveBeenCalled();
  });

  it("shows server field errors on the matching field", async () => {
    createShipment.mockRejectedValue(
      new ApiError({
        status: 400,
        code: "VALIDATION_FAILED",
        detail: "Request has 1 invalid field.",
        fieldErrors: { destinationId: "warehouse 3 does not exist" },
      }),
    );
    const { user } = renderWithProviders(<ShipmentsPage />);
    const form = await openAddForm(user);

    await fillNewShipment(user, form);
    await user.click(form.getByRole("button", { name: "Save shipment" }));

    await waitFor(() =>
      expect(
        form.getByRole("combobox", { name: /To warehouse/ }),
      ).toHaveAccessibleDescription("warehouse 3 does not exist"),
    );
    expect(form.getByRole("alert")).toHaveTextContent(
      "The shipment could not be saved. Fix the highlighted fields.",
    );
    expect(screen.getByRole("dialog", { name: "Add shipment" })).toBeVisible();
  });

  it("edits a shipment with its values prefilled", async () => {
    updateShipment.mockResolvedValue(URGENT_LUBLIN);
    const { user } = renderWithProviders(<ShipmentsPage />);

    await user.click(
      await screen.findByRole("button", { name: "Edit PO-1041" }),
    );
    const form = dialog("Edit shipment PO-1041");
    expect(form.getByRole("textbox", { name: /Reference/ })).toHaveValue(
      "PO-1041",
    );
    expect(form.getByLabelText(/Deadline/)).toHaveValue("2026-11-13T20:00");
    expect(form.getByRole("radio", { name: "Urgent" })).toBeChecked();
    await user.click(form.getByRole("button", { name: "Save shipment" }));

    expect(updateShipment).toHaveBeenCalledWith(1, {
      reference: "PO-1041",
      originId: 1,
      destinationId: 8,
      pallets: 6,
      weightKg: 3600,
      priority: "URGENT",
      pinnedFirst: false,
      deadline: "2026-11-13T20:00:00",
    });
  });

  it("deletes a shipment after confirmation", async () => {
    deleteShipment.mockResolvedValue(null);
    const { user } = renderWithProviders(<ShipmentsPage />);

    await user.click(
      await screen.findByRole("button", { name: "Delete PO-1042" }),
    );
    const confirm = dialog("Delete PO-1042?");
    expect(confirm.getByRole("button", { name: "Cancel" })).toHaveFocus();
    await user.click(confirm.getByRole("button", { name: "Delete" }));

    expect(deleteShipment).toHaveBeenCalledWith(2);
    expect(
      await screen.findByText("Shipment PO-1042 deleted"),
    ).toBeInTheDocument();
  });

  it("shows a warehouse load error and keeps Add shipment disabled", async () => {
    listWarehouses.mockRejectedValue(
      new ApiError({ status: 0, code: "NETWORK", detail: "Server down." }),
    );
    renderWithProviders(<ShipmentsPage />);

    expect(
      await screen.findByText("Could not load warehouses"),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add shipment" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Edit PO-1041" })).toBeDisabled();
  });

  it("does not save a half-filled deadline as no deadline", async () => {
    const { user } = renderWithProviders(<ShipmentsPage />);
    const form = await openAddForm(user);

    await fillNewShipment(user, form);
    // jsdom cannot type a partial date; a browser reports it as value "" + badInput.
    const deadline = form.getByLabelText(/Deadline/);
    Object.defineProperty(deadline, "validity", {
      configurable: true,
      value: { ...deadline.validity, badInput: true },
    });
    await user.click(form.getByRole("button", { name: "Save shipment" }));

    expect(deadline).toHaveAccessibleDescription(
      /Enter both a date and a time, or clear the field/,
    );
    expect(createShipment).not.toHaveBeenCalled();
  });

  it("closes the form with the named close button", async () => {
    const { user } = renderWithProviders(<ShipmentsPage />);
    await openAddForm(user);

    await user.click(screen.getByRole("button", { name: "Close dialog" }));

    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
  });

  it("announces success as a status, not an alert", async () => {
    deleteShipment.mockResolvedValue(null);
    const { user } = renderWithProviders(<ShipmentsPage />);

    await user.click(
      await screen.findByRole("button", { name: "Delete PO-1042" }),
    );
    await user.click(
      dialog("Delete PO-1042?").getByRole("button", { name: "Delete" }),
    );

    const message = await screen.findByText("Shipment PO-1042 deleted");
    expect(message.closest('[role="status"]')).not.toBeNull();
    expect(message.closest('[role="alert"]')).toBeNull();
    expect(
      screen.getByRole("button", { name: "Close notification" }),
    ).toBeInTheDocument();
  });

  it("clears the filters from the filtered empty state", async () => {
    const { user } = renderWithProviders(<ShipmentsPage />);
    await screen.findByText("PO-1041");

    listShipments.mockResolvedValueOnce([]);
    await user.click(screen.getByRole("radio", { name: "Normal" }));
    await user.click(
      await screen.findByRole("button", { name: "Clear filters" }),
    );

    await waitFor(() =>
      expect(listShipments).toHaveBeenLastCalledWith({
        priority: undefined,
        originId: undefined,
      }),
    );
    expect(await screen.findByText("PO-1041")).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "All" })).toBeChecked();
  });

  it("marks the list busy and hides the count while reloading", async () => {
    const { user } = renderWithProviders(<ShipmentsPage />);
    await screen.findByText("2 shipments waiting to be planned");

    listShipments.mockReturnValue(new Promise(() => {}));
    await user.click(screen.getByRole("radio", { name: "Urgent" }));

    const table = screen.getByRole("table", { name: "Shipments" });
    expect(table.closest("[aria-busy]")).toHaveAttribute("aria-busy", "true");
    expect(
      screen.queryByText(/shipments? (waiting|match)/),
    ).not.toBeInTheDocument();
  });

  it("asks for a warehouse first when there are none", async () => {
    listShipments.mockResolvedValue([]);
    listWarehouses.mockResolvedValue([]);
    const { user } = renderWithProviders(<ShipmentsPage />);
    await screen.findByRole("heading", { name: "No shipments yet" });

    await user.click(
      screen.getAllByRole("button", { name: "Add shipment" })[0],
    );

    const add = dialog("Add shipment");
    expect(add.getByText(/Add a warehouse first/)).toBeInTheDocument();
    expect(add.getByRole("link", { name: "Go to Warehouses" })).toHaveAttribute(
      "href",
      "/warehouses",
    );
    expect(add.queryByRole("textbox")).not.toBeInTheDocument();
  });
});

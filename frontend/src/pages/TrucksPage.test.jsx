import { screen, waitFor, within } from "@testing-library/react";
import { ApiError } from "../api/client.js";
import {
  createTruck,
  deleteTruck,
  listTrucks,
  setTruckAvailable,
  updateTruck,
} from "../api/trucks.js";
import { listWarehouses } from "../api/warehouses.js";
import { renderWithProviders } from "../test/renderWithProviders.jsx";
import { TrucksPage } from "./TrucksPage.jsx";

vi.mock("../api/trucks.js");
vi.mock("../api/warehouses.js");

const WAW = { id: 1, name: "WAW Central", city: "Warsaw" };
const POZ = { id: 4, name: "POZ West", city: "Poznan" };

const TRUCK = {
  id: 1,
  plate: "WX 1001A",
  capacityPallets: 33,
  payloadKg: 24000,
  baseConsumptionLPer100Km: 28,
  extraConsumptionFullLoadLPer100Km: 10,
  driverCostPlnPerHour: 45,
  homeWarehouse: WAW,
  available: true,
};

function dialog(name) {
  return within(screen.getByRole("dialog", { name }));
}

async function fillNewTruck(user, form) {
  await user.type(form.getByRole("textbox", { name: /Plate/ }), "wx 9009z");
  await user.type(form.getByRole("textbox", { name: /Capacity/ }), "18");
  await user.type(form.getByRole("textbox", { name: /Payload/ }), "12000");
  await user.type(
    form.getByRole("textbox", { name: /Base consumption/ }),
    "22.5",
  );
  await user.type(
    form.getByRole("textbox", { name: /Extra consumption/ }),
    "7",
  );
  await user.type(form.getByRole("textbox", { name: /Driver cost/ }), "42");
  await user.selectOptions(
    form.getByRole("combobox", { name: /Home depot/ }),
    "POZ West (Poznan)",
  );
}

describe("TrucksPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    listTrucks.mockResolvedValue([TRUCK]);
    listWarehouses.mockResolvedValue([POZ, WAW]);
  });

  it("lists trucks with capacity, payload and consumption", async () => {
    renderWithProviders(<TrucksPage />);

    const table = await screen.findByRole("table", { name: "Trucks" });
    const row = within(table).getAllByRole("row")[1];
    expect(row).toHaveTextContent("WX 1001A");
    expect(row).toHaveTextContent("33 pallets");
    expect(row).toHaveTextContent(/24\s000\skg/);
    expect(row).toHaveTextContent("28 + 10 at full load");
    expect(row).toHaveTextContent("45 PLN/h");
    expect(row).toHaveTextContent("WAW Central");
    expect(row).toHaveTextContent("Available");
  });

  it("shows an empty state", async () => {
    listTrucks.mockResolvedValue([]);
    renderWithProviders(<TrucksPage />);

    expect(
      await screen.findByRole("heading", { name: "No trucks yet" }),
    ).toBeInTheDocument();
  });

  it("shows a load error and retries", async () => {
    listTrucks
      .mockRejectedValueOnce(
        new ApiError({ status: 500, code: "INTERNAL_ERROR", detail: "Boom." }),
      )
      .mockResolvedValueOnce([TRUCK]);
    const { user } = renderWithProviders(<TrucksPage />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load trucks",
    );
    await user.click(screen.getByRole("button", { name: "Retry" }));

    expect(await screen.findByText("WX 1001A")).toBeInTheDocument();
  });

  it("creates a truck and confirms it", async () => {
    createTruck.mockResolvedValue({ ...TRUCK, id: 6, plate: "WX 9009Z" });
    const { user } = renderWithProviders(<TrucksPage />);
    await screen.findByText("WX 1001A");

    await user.click(screen.getByRole("button", { name: "Add truck" }));
    const form = dialog("Add truck");
    await fillNewTruck(user, form);
    await user.click(form.getByRole("button", { name: "Save truck" }));

    expect(createTruck).toHaveBeenCalledWith({
      plate: "wx 9009z",
      capacityPallets: 18,
      payloadKg: 12000,
      baseConsumptionLPer100Km: 22.5,
      extraConsumptionFullLoadLPer100Km: 7,
      driverCostPlnPerHour: 42,
      homeWarehouseId: 4,
    });
    expect(await screen.findByText("Truck WX 9009Z saved")).toBeInTheDocument();
    expect(listTrucks).toHaveBeenCalledTimes(2);
  });

  it("checks the backend ranges on the client", async () => {
    const { user } = renderWithProviders(<TrucksPage />);
    await screen.findByText("WX 1001A");

    await user.click(screen.getByRole("button", { name: "Add truck" }));
    const form = dialog("Add truck");
    await user.type(form.getByRole("textbox", { name: /Capacity/ }), "41");
    await user.type(
      form.getByRole("textbox", { name: /Base consumption/ }),
      "0",
    );
    await user.click(form.getByRole("button", { name: "Save truck" }));

    expect(
      form.getByRole("textbox", { name: /Capacity/ }),
    ).toHaveAccessibleDescription(/Capacity must be between 1 and 40/);
    expect(
      form.getByRole("textbox", { name: /Base consumption/ }),
    ).toHaveAccessibleDescription(
      /Base consumption must be greater than 0 and at most 100/,
    );
    expect(createTruck).not.toHaveBeenCalled();
  });

  it("shows server field errors on the matching field and duplicates in the form", async () => {
    createTruck.mockRejectedValueOnce(
      new ApiError({
        status: 400,
        code: "VALIDATION_FAILED",
        detail: "Request has 1 invalid field.",
        fieldErrors: { payloadKg: "must be less than or equal to 40000" },
      }),
    );
    createTruck.mockRejectedValueOnce(
      new ApiError({
        status: 409,
        code: "CONFLICT",
        detail: "A truck with plate 'WX 9009Z' already exists.",
      }),
    );
    const { user } = renderWithProviders(<TrucksPage />);
    await screen.findByText("WX 1001A");

    await user.click(screen.getByRole("button", { name: "Add truck" }));
    const form = dialog("Add truck");
    await fillNewTruck(user, form);
    await user.click(form.getByRole("button", { name: "Save truck" }));

    await waitFor(() =>
      expect(
        form.getByRole("textbox", { name: /Payload/ }),
      ).toHaveAccessibleDescription(/must be less than or equal to 40000/),
    );

    await user.click(form.getByRole("button", { name: "Save truck" }));

    await waitFor(() =>
      expect(form.getByRole("alert")).toHaveTextContent(
        "A truck with plate 'WX 9009Z' already exists.",
      ),
    );
  });

  it("edits a truck", async () => {
    updateTruck.mockResolvedValue({ ...TRUCK, driverCostPlnPerHour: 50 });
    const { user } = renderWithProviders(<TrucksPage />);

    await user.click(
      await screen.findByRole("button", { name: "Edit WX 1001A" }),
    );
    const form = dialog("Edit WX 1001A");
    const cost = form.getByRole("textbox", { name: /Driver cost/ });
    await user.clear(cost);
    await user.type(cost, "50");
    await user.click(form.getByRole("button", { name: "Save truck" }));

    expect(updateTruck).toHaveBeenCalledWith(1, {
      plate: "WX 1001A",
      capacityPallets: 33,
      payloadKg: 24000,
      baseConsumptionLPer100Km: 28,
      extraConsumptionFullLoadLPer100Km: 10,
      driverCostPlnPerHour: 50,
      homeWarehouseId: 1,
    });
    expect(await screen.findByText("Truck WX 1001A saved")).toBeInTheDocument();
  });

  it("deletes a truck after confirmation", async () => {
    deleteTruck.mockResolvedValue(null);
    const { user } = renderWithProviders(<TrucksPage />);

    await user.click(
      await screen.findByRole("button", { name: "Delete WX 1001A" }),
    );
    const confirm = dialog("Delete WX 1001A?");
    expect(confirm.getByRole("button", { name: "Cancel" })).toHaveFocus();
    await user.click(confirm.getByRole("button", { name: "Delete" }));

    expect(deleteTruck).toHaveBeenCalledWith(1);
    expect(
      await screen.findByText("Truck WX 1001A deleted"),
    ).toBeInTheDocument();
    expect(listTrucks).toHaveBeenCalledTimes(2);
  });

  it("keeps the confirmation open while the delete runs", async () => {
    deleteTruck.mockReturnValue(new Promise(() => {}));
    const { user } = renderWithProviders(<TrucksPage />);

    await user.click(
      await screen.findByRole("button", { name: "Delete WX 1001A" }),
    );
    const confirm = dialog("Delete WX 1001A?");
    await user.click(confirm.getByRole("button", { name: "Delete" }));
    await user.keyboard("{Escape}");

    expect(
      screen.getByRole("dialog", { name: "Delete WX 1001A?" }),
    ).toBeInTheDocument();
    expect(confirm.getByRole("button", { name: "Cancel" })).toBeDisabled();
  });

  it("keeps the confirmation open with the server message when delete fails", async () => {
    deleteTruck.mockRejectedValue(
      new ApiError({
        status: 404,
        code: "NOT_FOUND",
        detail: "Truck 1 not found.",
      }),
    );
    const { user } = renderWithProviders(<TrucksPage />);

    await user.click(
      await screen.findByRole("button", { name: "Delete WX 1001A" }),
    );
    const confirm = dialog("Delete WX 1001A?");
    await user.click(confirm.getByRole("button", { name: "Delete" }));

    expect(await confirm.findByRole("alert")).toHaveTextContent(
      "Truck 1 not found.",
    );
  });

  it("requires a home depot", async () => {
    const { user } = renderWithProviders(<TrucksPage />);
    await screen.findByText("WX 1001A");

    await user.click(screen.getByRole("button", { name: "Add truck" }));
    const form = dialog("Add truck");
    await user.click(form.getByRole("button", { name: "Save truck" }));

    expect(
      form.getByRole("combobox", { name: /Home depot/ }),
    ).toHaveAccessibleDescription(/Choose the home depot/);
    expect(createTruck).not.toHaveBeenCalled();
  });

  it("marks a truck on route and back from the table", async () => {
    setTruckAvailable.mockResolvedValueOnce({ ...TRUCK, available: false });
    listTrucks
      .mockResolvedValueOnce([TRUCK])
      .mockResolvedValueOnce([{ ...TRUCK, available: false }]);
    setTruckAvailable.mockResolvedValueOnce(TRUCK);
    const { user } = renderWithProviders(<TrucksPage />);

    await user.click(
      await screen.findByRole("button", { name: "Mark WX 1001A on route" }),
    );

    expect(setTruckAvailable).toHaveBeenCalledWith(1, false);
    expect(
      await screen.findByText("Truck WX 1001A is on route"),
    ).toBeInTheDocument();
    expect(await screen.findByText("On route")).toBeInTheDocument();

    await user.click(
      screen.getByRole("button", { name: "Mark WX 1001A available" }),
    );
    expect(setTruckAvailable).toHaveBeenLastCalledWith(1, true);
  });

  it("shows why the status could not be changed", async () => {
    setTruckAvailable.mockRejectedValue(
      new ApiError({ status: 0, code: "NETWORK", detail: "Cannot reach." }),
    );
    const { user } = renderWithProviders(<TrucksPage />);

    await user.click(
      await screen.findByRole("button", { name: "Mark WX 1001A on route" }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "WX 1001A: Cannot reach.",
    );
  });
});

import { screen, within } from "@testing-library/react";
import { Route, Routes } from "react-router";
import { ApiError } from "../api/client.js";
import { createPlan } from "../api/plans.js";
import { listShipments } from "../api/shipments.js";
import { listTrucks, recommendTrucks } from "../api/trucks.js";
import { listWarehouses } from "../api/warehouses.js";
import { renderWithProviders } from "../test/renderWithProviders.jsx";
import { PlanPage } from "./PlanPage.jsx";

vi.mock("../api/plans.js");
vi.mock("../api/shipments.js");
vi.mock("../api/trucks.js");
vi.mock("../api/warehouses.js");

const WAW = { id: 1, name: "WAW Central", city: "Warsaw" };
const GDN = { id: 3, name: "GDN Port", city: "Gdansk" };

function shipment(id, reference, fields = {}) {
  return {
    id,
    reference,
    origin: WAW,
    destination: GDN,
    pallets: 6,
    weightKg: 3600,
    priority: "NORMAL",
    deadline: null,
    pinnedFirst: false,
    ...fields,
  };
}

const SHIPMENTS = [
  shipment(1, "PO-1041", { priority: "URGENT" }),
  shipment(2, "PO-1042", { priority: "HIGH", pinnedFirst: true }),
  shipment(4, "PO-1044"),
];

function truck(id, plate, fields = {}) {
  return {
    id,
    plate,
    capacityPallets: 33,
    payloadKg: 24000,
    homeWarehouse: WAW,
    available: true,
    ...fields,
  };
}

const TRUCKS = [
  truck(1, "WX 1001A"),
  truck(2, "WX 2002B"),
  truck(5, "PO 6006F", { homeWarehouse: GDN }),
  truck(7, "WX 3003C", { available: false }),
];

beforeEach(() => {
  listWarehouses.mockResolvedValue([GDN, WAW]);
  listTrucks.mockResolvedValue(TRUCKS);
  listShipments.mockResolvedValue(SHIPMENTS);
});

function renderPage(state) {
  return renderWithProviders(
    <Routes>
      <Route path="/" element={<PlanPage />} />
      <Route path="/plans/:id" element={<h1>Result page</h1>} />
    </Routes>,
    { route: state ? { pathname: "/", state } : "/" },
  );
}

async function chooseDepot(user, name = "WAW Central (Warsaw)") {
  await user.selectOptions(
    await screen.findByRole("combobox", { name: /Depot/ }),
    name,
  );
  await screen.findByRole("checkbox", { name: /PO-1041/ });
}

describe("PlanPage", () => {
  it("lists the depot's shipments and all trucks preselected and plans the run", async () => {
    createPlan.mockResolvedValue({ id: "plan-1" });
    const { user } = renderPage();
    await chooseDepot(user);

    expect(listShipments).toHaveBeenLastCalledWith({ originId: 1 });
    expect(screen.getByRole("checkbox", { name: /PO-1041/ })).toBeChecked();
    expect(
      screen.getByRole("checkbox", { name: /PO-1042.*Pinned first/ }),
    ).toBeChecked();
    expect(screen.getByRole("checkbox", { name: /WX 2002B/ })).toBeChecked();

    await user.click(screen.getByRole("checkbox", { name: /PO-1044/ }));
    await user.click(screen.getByRole("button", { name: "Plan the run" }));

    expect(createPlan).toHaveBeenCalledWith({
      depotId: 1,
      shipmentIds: [1, 2],
      truckIds: [1, 2],
      returnToDepot: true,
      departureTime: null,
    });
    expect(
      await screen.findByRole("heading", { name: "Result page" }),
    ).toBeInTheDocument();
  });

  it("asks for a depot and at least one truck before planning", async () => {
    const { user } = renderPage();
    await user.click(
      await screen.findByRole("button", { name: "Plan the run" }),
    );

    expect(await screen.findByText("Choose the depot")).toBeInTheDocument();

    await chooseDepot(user);
    await user.click(screen.getByRole("button", { name: "Clear trucks" }));
    await user.click(screen.getByRole("button", { name: "Plan the run" }));

    expect(
      await screen.findByText("Select at least one truck"),
    ).toBeInTheDocument();
    expect(createPlan).not.toHaveBeenCalled();
  });

  it("shows server field errors next to the matching list", async () => {
    createPlan.mockRejectedValue(
      new ApiError({
        status: 400,
        code: "VALIDATION_FAILED",
        detail: "Request has 1 invalid field.",
        fieldErrors: { shipmentIds: "shipment 4 does not exist" },
      }),
    );
    const { user } = renderPage();
    await chooseDepot(user);
    await user.click(screen.getByRole("button", { name: "Plan the run" }));

    expect(
      await screen.findByText("shipment 4 does not exist"),
    ).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent(
      "The plan could not be created. Fix the highlighted fields.",
    );
  });

  it("shows a readable message when the server cannot be reached", async () => {
    createPlan.mockRejectedValue(
      new ApiError({
        status: 0,
        code: "NETWORK",
        detail: "Cannot reach the server.",
      }),
    );
    const { user } = renderPage();
    await chooseDepot(user);
    await user.click(screen.getByRole("button", { name: "Plan the run" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Cannot reach the server.",
    );
    expect(screen.getByRole("button", { name: "Plan the run" })).toBeEnabled();
  });

  it("asks for warehouses first when there are none", async () => {
    listWarehouses.mockResolvedValue([]);
    renderPage();

    expect(await screen.findByText("No warehouses yet")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Go to Warehouses" }),
    ).toHaveAttribute("href", "/warehouses");
  });

  it("prefills the form from a previous run (Edit run)", async () => {
    createPlan.mockResolvedValue({ id: "plan-2" });
    const { user } = renderPage({
      request: {
        depotId: 1,
        shipmentIds: [2],
        truckIds: [2],
        returnToDepot: false,
        departureTime: "2026-11-14T07:30:00",
      },
    });
    const shipments = within(
      await screen.findByRole("region", { name: "Shipments" }),
    );

    expect(
      await shipments.findByRole("checkbox", { name: /PO-1042/ }),
    ).toBeChecked();
    expect(
      shipments.getByRole("checkbox", { name: /PO-1041/ }),
    ).not.toBeChecked();
    expect(screen.getByLabelText(/Departure/)).toHaveValue("2026-11-14T07:30");

    await user.click(screen.getByRole("button", { name: "Plan the run" }));

    expect(createPlan).toHaveBeenCalledWith({
      depotId: 1,
      shipmentIds: [2],
      truckIds: [2],
      returnToDepot: false,
      departureTime: "2026-11-14T07:30:00",
    });
  });

  it("recommends trucks for the selected depot and shipments", async () => {
    recommendTrucks.mockResolvedValue({
      truckIds: [1],
      trucks: [{ id: 1, plate: "WX 1001A" }],
      totals: { costPln: 412 },
      utilisationPct: 100,
      unassigned: [],
    });
    const { user } = renderPage();
    await chooseDepot(user);

    await user.click(screen.getByRole("button", { name: "Recommend trucks" }));

    expect(recommendTrucks).toHaveBeenCalledWith({
      depotId: 1,
      shipmentIds: [1, 2, 4],
      returnToDepot: true,
    });
    expect(
      await screen.findByText(
        "Recommended: WX 1001A — 100% pallets, 412.00 PLN estimated",
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: /WX 1001A/ })).toBeChecked();
    expect(
      screen.getByRole("checkbox", { name: /WX 2002B/ }),
    ).not.toBeChecked();
  });

  it("clears the recommendation note when the depot changes", async () => {
    recommendTrucks.mockResolvedValue({
      truckIds: [1],
      trucks: [{ id: 1, plate: "WX 1001A" }],
      totals: { costPln: 412 },
      utilisationPct: 100,
      unassigned: [],
    });
    const { user } = renderPage();
    await chooseDepot(user);
    await user.click(screen.getByRole("button", { name: "Recommend trucks" }));
    await screen.findByText(
      "Recommended: WX 1001A — 100% pallets, 412.00 PLN estimated",
    );

    await chooseDepot(user, "GDN Port (Gdansk)");

    expect(
      screen.queryByText(
        "Recommended: WX 1001A — 100% pallets, 412.00 PLN estimated",
      ),
    ).not.toBeInTheDocument();
  });

  it("is disabled until a depot and a shipment are selected", async () => {
    const { user } = renderPage();

    expect(
      await screen.findByRole("button", { name: "Recommend trucks" }),
    ).toBeDisabled();

    await chooseDepot(user);

    expect(
      screen.getByRole("button", { name: "Recommend trucks" }),
    ).toBeEnabled();
  });

  it("shows which shipments do not fit anywhere instead of failing", async () => {
    recommendTrucks.mockResolvedValue({
      truckIds: [],
      trucks: [],
      totals: null,
      utilisationPct: null,
      unassigned: [
        {
          shipmentId: 99,
          reference: "PO-99",
          reason: "EXCEEDS_LARGEST_TRUCK",
          message: "40 pallets exceed the largest selected truck (33 pallets).",
        },
      ],
    });
    const { user } = renderPage();
    await chooseDepot(user);

    await user.click(screen.getByRole("button", { name: "Recommend trucks" }));

    expect(
      await screen.findByText(
        "PO-99: 40 pallets exceed the largest selected truck (33 pallets).",
      ),
    ).toBeInTheDocument();
  });

  it("shows a readable message when the recommendation call fails", async () => {
    recommendTrucks.mockRejectedValue(
      new ApiError({
        status: 0,
        code: "NETWORK",
        detail: "Cannot reach the server.",
      }),
    );
    const { user } = renderPage();
    await chooseDepot(user);

    await user.click(screen.getByRole("button", { name: "Recommend trucks" }));

    expect(
      await screen.findByText("Cannot reach the server."),
    ).toBeInTheDocument();
  });

  it("lists only the available trucks based at the chosen depot", async () => {
    const { user } = renderPage();
    await chooseDepot(user);
    const trucks = within(screen.getByRole("region", { name: "Trucks" }));

    expect(trucks.getAllByRole("checkbox").map((box) => box.value)).toEqual([
      "1",
      "2",
    ]);
    expect(trucks.getByText("2 of 2 selected")).toBeInTheDocument();
    expect(
      trucks.queryByRole("checkbox", { name: /PO 6006F/ }),
    ).not.toBeInTheDocument();
    expect(
      trucks.queryByRole("checkbox", { name: /WX 3003C/ }),
    ).not.toBeInTheDocument();
  });

  it("switches the truck list with the depot and says when it is empty", async () => {
    listShipments.mockImplementation(({ originId }) =>
      Promise.resolve(
        originId === 3 ? [shipment(9, "PO-2001", { origin: GDN })] : SHIPMENTS,
      ),
    );
    listTrucks.mockResolvedValue([
      ...TRUCKS,
      truck(8, "PO 7007G", { homeWarehouse: GDN, available: false }),
    ]);
    const { user } = renderPage();
    const trucks = () => within(screen.getByRole("region", { name: "Trucks" }));

    expect(
      await screen.findByText("Choose a depot to see its trucks."),
    ).toBeInTheDocument();

    await user.selectOptions(
      screen.getByRole("combobox", { name: /Depot/ }),
      "GDN Port (Gdansk)",
    );
    expect(
      await trucks().findByRole("checkbox", { name: /PO 6006F/ }),
    ).toBeChecked();
    expect(trucks().getAllByRole("checkbox")).toHaveLength(1);
  });

  it("explains an empty truck list when every truck of the depot is on route", async () => {
    listTrucks.mockResolvedValue([
      truck(1, "WX 1001A", { available: false }),
      truck(5, "PO 6006F", { homeWarehouse: GDN }),
    ]);
    const { user } = renderPage();
    await chooseDepot(user);

    expect(
      screen.getByRole("heading", {
        name: "No available trucks at this depot",
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/The only truck based at this depot is on route/),
    ).toBeInTheDocument();
  });
});

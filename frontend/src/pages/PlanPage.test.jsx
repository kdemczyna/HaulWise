import { screen, within } from "@testing-library/react";
import { Route, Routes } from "react-router";
import { ApiError } from "../api/client.js";
import { createPlan } from "../api/plans.js";
import { listShipments } from "../api/shipments.js";
import { listTrucks } from "../api/trucks.js";
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

const TRUCKS = [
  { id: 1, plate: "WX 1001A", capacityPallets: 33, payloadKg: 24000 },
  { id: 2, plate: "WX 2002B", capacityPallets: 33, payloadKg: 24000 },
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
      "Fix the highlighted fields",
    );
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
});

import { screen, waitFor, within } from "@testing-library/react";
import { Route, Routes } from "react-router";
import { ApiError } from "../api/client.js";
import { createPlan, getPlan } from "../api/plans.js";
import { DEMO_PLAN } from "../test/fixtures/plan.js";
import { renderWithProviders } from "../test/renderWithProviders.jsx";
import { PlanResultPage } from "./PlanResultPage.jsx";

vi.mock("../api/plans.js");
vi.mock("../api/trucks.js", () => ({
  listTrucks: vi.fn(() =>
    Promise.resolve([
      { id: 1, plate: "WX 1001A" },
      { id: 2, plate: "WX 2002B" },
      { id: 3, plate: "WX 3003C" },
    ]),
  ),
}));
// Leaflet needs a real browser layout; the map renders its markers' titles as text instead.
vi.mock("react-leaflet", () => ({
  MapContainer: ({ children }) => <div>{children}</div>,
  TileLayer: () => null,
  Polyline: () => null,
  Marker: ({ title }) => <span>{title}</span>,
}));

function renderPage(plan = DEMO_PLAN) {
  getPlan.mockResolvedValue(plan);
  return renderWithProviders(
    <Routes>
      <Route path="/plans/:id" element={<PlanResultPage />} />
    </Routes>,
    { route: `/plans/${plan.id}` },
  );
}

function truckCard(plate) {
  return within(screen.getByRole("article", { name: `Truck ${plate}` }));
}

function stopTexts(plate) {
  return truckCard(plate)
    .getAllByRole("listitem")
    .map((item) => item.textContent);
}

describe("PlanResultPage", () => {
  it("shows the cost of priority as delivered by the backend", async () => {
    renderPage();

    expect(
      await screen.findByText(
        "Delivering Gdansk first costs 292.97 PLN more (85 km and 1 h 15 min longer).",
      ),
    ).toBeInTheDocument();
    const banner = within(
      screen.getByRole("region", { name: "Cost of priority" }),
    );
    expect(banner.getByText("+292.97 PLN")).toBeInTheDocument();
    expect(banner.getByText("+1 h 15 min")).toBeInTheDocument();
    expect(banner.getByText(/Deadlines missed: 1 → 0/)).toBeInTheDocument();
    expect(getPlan).toHaveBeenCalledWith(DEMO_PLAN.id);
  });

  it("compares the totals with the delta as the difference column", async () => {
    renderPage();

    const table = within(
      await screen.findByRole("table", { name: "Optimal vs priority totals" }),
    );
    const costRow = table.getByRole("row", { name: /Total cost/ });
    // toHaveTextContent folds the no-break spaces of the number format into spaces.
    expect(costRow).toHaveTextContent("9 849.88 PLN10 142.86 PLN+292.97 PLN");
    expect(table.getByRole("row", { name: /Distance/ })).toHaveTextContent(
      "+85 km",
    );
    expect(
      table.getByRole("row", { name: /Deadlines missed/ }),
    ).toHaveTextContent("10-1");
  });

  it("switches the map and the truck cards between the optimal and the priority plan", async () => {
    const { user } = renderPage();
    await screen.findByRole("article", { name: "Truck WX 1001A" });

    expect(
      screen.getByRole("region", { name: "Map of the optimal routes" }),
    ).toBeInTheDocument();
    const optimalStops = stopTexts("WX 1001A");
    expect(optimalStops[0]).toContain("Lublin");
    expect(optimalStops[2]).toContain("Gdansk");
    expect(optimalStops[2]).toContain("Pinned first - ignored by optimal");
    expect(optimalStops[2]).toContain("11 h 18 min late");

    await user.click(screen.getByRole("radio", { name: "Priority" }));

    expect(
      screen.getByRole("region", { name: "Map of the priority routes" }),
    ).toBeInTheDocument();
    const priorityStops = stopTexts("WX 1001A");
    expect(priorityStops[0]).toContain("Gdansk");
    expect(priorityStops[0]).toContain("on time");
    expect(priorityStops[0]).not.toContain("ignored by optimal");
  });

  it("collapses identical routes and lists unused trucks, unloaded shipments and warnings", async () => {
    renderPage();
    await screen.findByRole("article", { name: "Truck WX 2002B" });

    expect(
      truckCard("WX 2002B").getByText("Same in both plans"),
    ).toBeInTheDocument();
    expect(truckCard("WX 1001A").queryByText("Same in both plans")).toBeNull();
    expect(screen.getByText("Not used: WX 3003C")).toBeInTheDocument();
    expect(
      screen.getByText(/36 pallets exceed the largest selected truck/),
    ).toBeInTheDocument();
    expect(screen.getByText(/WX 1001A arrives in Gdansk/)).toHaveTextContent(
      "Optimal plan:",
    );
  });

  it("shows the no-extra-cost variant", async () => {
    renderPage({
      ...DEMO_PLAN,
      delta: {
        ...DEMO_PLAN.delta,
        costPln: 0,
        distanceKm: 0,
        durationMinutes: 0,
        litres: 0,
        noExtraCost: true,
        sentence: "Respecting priorities costs nothing extra.",
      },
    });

    expect(await screen.findByText("No extra cost")).toBeInTheDocument();
    expect(
      screen.getByText("Respecting priorities costs nothing extra."),
    ).toBeInTheDocument();
    expect(
      within(
        screen.getByRole("region", { name: "Cost of priority" }),
      ).queryByText("+292.97 PLN"),
    ).toBeNull();
  });

  it("shows the nothing-to-compare variant when no shipment was loaded", async () => {
    const sentence =
      "No shipments could be loaded onto the selected trucks, so there is nothing to compare.";
    const emptyVariant = {
      ...DEMO_PLAN.optimal,
      routes: [],
      deadlinesMissed: 0,
    };
    renderPage({
      ...DEMO_PLAN,
      optimal: emptyVariant,
      priority: { ...emptyVariant, mode: "PRIORITY" },
      delta: { ...DEMO_PLAN.delta, noExtraCost: true, sentence },
    });

    expect(await screen.findByText(sentence)).toBeInTheDocument();
    expect(screen.queryByText("No extra cost")).toBeNull();
    expect(
      screen.getByText("No truck was loaded in this plan."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Not used: WX 1001A, WX 2002B, WX 3003C"),
    ).toBeInTheDocument();
  });

  it("plans the same run again and opens the new plan", async () => {
    createPlan.mockResolvedValue({ id: "plan-2" });
    const { user } = renderPage();
    await user.click(await screen.findByRole("button", { name: "Plan again" }));

    expect(createPlan).toHaveBeenCalledWith(DEMO_PLAN.request);
    await waitFor(() => expect(getPlan).toHaveBeenLastCalledWith("plan-2"));
  });

  it("shows a load error and retries", async () => {
    getPlan
      .mockRejectedValueOnce(
        new ApiError({
          status: 0,
          code: "NETWORK",
          detail: "Cannot reach the server.",
        }),
      )
      .mockResolvedValue(DEMO_PLAN);
    const { user } = renderWithProviders(
      <Routes>
        <Route path="/plans/:id" element={<PlanResultPage />} />
      </Routes>,
      { route: `/plans/${DEMO_PLAN.id}` },
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Cannot reach the server.",
    );
    await user.click(screen.getByRole("button", { name: "Retry" }));

    expect(
      await screen.findByRole("region", { name: "Cost of priority" }),
    ).toBeInTheDocument();
  });

  it("explains that the plan is gone after a restart", async () => {
    getPlan.mockRejectedValue(
      new ApiError({
        status: 404,
        code: "NOT_FOUND",
        detail: "Plan not found",
      }),
    );
    renderWithProviders(
      <Routes>
        <Route path="/plans/:id" element={<PlanResultPage />} />
      </Routes>,
      { route: "/plans/expired" },
    );

    expect(
      await screen.findByRole("heading", { name: "Plan not found" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/kept in memory and lost when the server restarts/),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Plan a run" })).toHaveAttribute(
      "href",
      "/",
    );
  });
});

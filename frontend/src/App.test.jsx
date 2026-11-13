import { screen, waitFor, within } from "@testing-library/react";
import App from "./App.jsx";
import { renderWithProviders } from "./test/renderWithProviders.jsx";

// Shell tests never reach the network: the pages' api modules answer with empty lists.
vi.mock("./api/warehouses.js", () => ({
  listWarehouses: vi.fn(() => Promise.resolve([])),
}));
vi.mock("./api/trucks.js", () => ({
  listTrucks: vi.fn(() => Promise.resolve([])),
}));
vi.mock("./api/shipments.js", () => ({
  listShipments: vi.fn(() => Promise.resolve([])),
}));
// The plan stays loading, so the result page never renders the map.
vi.mock("./api/plans.js", () => ({
  createPlan: vi.fn(),
  getPlan: vi.fn(() => new Promise(() => {})),
}));

function navigation() {
  return within(screen.getByRole("navigation", { name: "Main navigation" }));
}

function pageHeading() {
  return screen.getByRole("heading", { level: 1 });
}

describe("App shell", () => {
  it("renders the brand, the navigation and the Plan page at /", () => {
    renderWithProviders(<App />);

    expect(screen.getByRole("link", { name: "HaulWise" })).toHaveAttribute(
      "href",
      "/",
    );
    expect(
      navigation()
        .getAllByRole("link")
        .map((link) => link.textContent),
    ).toEqual(["Plan a run", "Shipments", "Trucks", "Warehouses", "Settings"]);
    expect(pageHeading()).toHaveTextContent("Plan a run");
    expect(
      navigation().getByRole("link", { name: "Plan a run" }),
    ).toHaveAttribute("aria-current", "page");
  });

  it.each([
    ["Shipments", "Shipments"],
    ["Trucks", "Trucks"],
    ["Warehouses", "Warehouses"],
    ["Settings", "Settings"],
  ])("navigates to %s", async (linkName, heading) => {
    const { user } = renderWithProviders(<App />);

    await user.click(navigation().getByRole("link", { name: linkName }));

    expect(pageHeading()).toHaveTextContent(heading);
    expect(navigation().getByRole("link", { name: linkName })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(
      navigation().getByRole("link", { name: "Plan a run" }),
    ).not.toHaveAttribute("aria-current");
  });

  it("shows the plan result route under Plan a run", async () => {
    renderWithProviders(<App />, { route: "/plans/abc-123" });

    expect(
      await screen.findByRole("heading", { level: 1, name: "Plan result" }),
    ).toBeInTheDocument();
    expect(
      navigation().getByRole("link", { name: "Plan a run" }),
    ).toHaveAttribute("aria-current", "page");
  });

  it("shows a not-found page with a way back for unknown routes", async () => {
    const { user } = renderWithProviders(<App />, { route: "/nowhere" });

    expect(pageHeading()).toHaveTextContent("Page not found");

    await user.click(screen.getByRole("link", { name: "Go to Plan a run" }));

    expect(pageHeading()).toHaveTextContent("Plan a run");
  });

  it("does not highlight Plan a run for /plans/ without an id", () => {
    renderWithProviders(<App />, { route: "/plans/" });

    expect(pageHeading()).toHaveTextContent("Page not found");
    expect(
      navigation().getByRole("link", { name: "Plan a run" }),
    ).not.toHaveAttribute("aria-current");
  });

  it("sets the document title and focuses the new page heading after navigation", async () => {
    const { user } = renderWithProviders(<App />);

    expect(document.title).toBe("Plan a run - HaulWise");
    expect(pageHeading()).not.toHaveFocus();

    await user.click(navigation().getByRole("link", { name: "Trucks" }));

    expect(document.title).toBe("Trucks - HaulWise");
    expect(pageHeading()).toHaveFocus();
  });

  it("offers a skip link to the main content", () => {
    renderWithProviders(<App />);

    expect(
      screen.getByRole("link", { name: "Skip to content" }),
    ).toHaveAttribute("href", "#main-content");
    expect(screen.getByRole("main")).toHaveAttribute("id", "main-content");
  });
});

describe("App shell below the sm breakpoint", () => {
  let originalMatchMedia;

  beforeEach(() => {
    originalMatchMedia = window.matchMedia;
    window.matchMedia = (query) => ({
      ...originalMatchMedia(query),
      matches: query.includes("max-width"),
    });
  });

  afterEach(() => {
    window.matchMedia = originalMatchMedia;
  });

  function burger(name) {
    return screen.getByLabelText(name, { selector: "button" });
  }

  it("makes the closed navigation inert and opens it with the burger", async () => {
    const { user } = renderWithProviders(<App />);
    const nav = screen.getByRole("navigation", { name: "Main navigation" });

    await waitFor(() => expect(nav).toHaveAttribute("inert"));

    // jsdom applies Mantine's `hiddenFrom="sm"` media rule unconditionally, so the
    // burger is not in jsdom's accessibility tree; `burger()` finds it by its label.
    await user.click(burger("Open navigation"));

    expect(nav).not.toHaveAttribute("inert");
    expect(burger("Close navigation")).toHaveAttribute("aria-expanded", "true");

    await user.click(within(nav).getByRole("link", { name: "Trucks" }));

    expect(pageHeading()).toHaveTextContent("Trucks");
    expect(nav).toHaveAttribute("inert");
  });
});

import { screen, within } from "@testing-library/react";
import { ApiError } from "../api/client.js";
import { listPlans } from "../api/plans.js";
import { renderWithProviders } from "../test/renderWithProviders.jsx";
import { RecentPlansPage } from "./RecentPlansPage.jsx";

vi.mock("../api/plans.js");

const SENT = {
  id: "c3",
  depotId: 4,
  depotName: "POZ West",
  createdAt: "2026-11-14T09:40:00",
  dispatchedAt: "2026-11-14T10:05:00",
  shipmentCount: 12,
  truckCount: 3,
};
const DRAFT = {
  id: "a1",
  depotId: 1,
  depotName: "WAW Central",
  createdAt: "2026-11-14T08:15:00",
  dispatchedAt: null,
  shipmentCount: 5,
  truckCount: 2,
};

function bodyRows() {
  const table = screen.getByRole("table", { name: "Recent plans" });
  return within(table).getAllByRole("row").slice(1);
}

describe("RecentPlansPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("shows a loading state, then the plans in the order the API returned them", async () => {
    listPlans.mockResolvedValue([SENT, DRAFT]);
    renderWithProviders(<RecentPlansPage />);

    expect(screen.getByText("Loading plans…")).toBeInTheDocument();
    await screen.findByRole("table", { name: "Recent plans" });

    const [first, second] = bodyRows();
    expect(first).toHaveTextContent("POZ West");
    expect(first).toHaveTextContent("14 Nov, 09:40");
    expect(second).toHaveTextContent("WAW Central");
    expect(second).toHaveTextContent("14 Nov, 08:15");
    expect(bodyRows()).toHaveLength(2);
  });

  it("keeps the API order instead of re-sorting", async () => {
    listPlans.mockResolvedValue([DRAFT, SENT]);
    renderWithProviders(<RecentPlansPage />);
    await screen.findByRole("table", { name: "Recent plans" });

    expect(
      bodyRows().map((row) => within(row).getAllByRole("cell")[0].textContent),
    ).toEqual(["WAW Central", "POZ West"]);
  });

  it("shows Sent with its time for a dispatched plan and Draft otherwise, plus the counts", async () => {
    listPlans.mockResolvedValue([SENT, DRAFT]);
    renderWithProviders(<RecentPlansPage />);
    await screen.findByRole("table", { name: "Recent plans" });

    const [sent, draft] = bodyRows();
    expect(sent).toHaveTextContent("Sent 14 Nov, 10:05");
    expect(sent).not.toHaveTextContent("Draft");
    expect(draft).toHaveTextContent("Draft");
    expect(draft).not.toHaveTextContent("Sent");
    const sentCells = within(sent).getAllByRole("cell");
    expect(sentCells[3]).toHaveTextContent("12");
    expect(sentCells[4]).toHaveTextContent("3");
  });

  it("links every row to its plan result page", async () => {
    listPlans.mockResolvedValue([SENT, DRAFT]);
    renderWithProviders(<RecentPlansPage />);

    expect(
      await screen.findByRole("link", {
        name: "Open plan from POZ West, created 14 Nov, 09:40",
      }),
    ).toHaveAttribute("href", "/plans/c3");
    expect(
      screen.getByRole("link", {
        name: "Open plan from WAW Central, created 14 Nov, 08:15",
      }),
    ).toHaveAttribute("href", "/plans/a1");
  });

  it("shows an empty state that leads to Plan a run", async () => {
    listPlans.mockResolvedValue([]);
    renderWithProviders(<RecentPlansPage />);

    expect(
      await screen.findByRole("heading", { name: "No plans yet" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Plan a run" })).toHaveAttribute(
      "href",
      "/",
    );
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("shows an error with Retry that reloads the list", async () => {
    listPlans
      .mockRejectedValueOnce(
        new ApiError({ status: 500, detail: "Server unavailable." }),
      )
      .mockResolvedValueOnce([DRAFT]);
    const { user } = renderWithProviders(<RecentPlansPage />);

    expect(await screen.findByText("Could not load plans")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Retry" }));

    expect(
      await screen.findByRole("table", { name: "Recent plans" }),
    ).toBeInTheDocument();
    expect(listPlans).toHaveBeenCalledTimes(2);
  });
});

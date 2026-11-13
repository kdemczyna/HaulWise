import { screen } from "@testing-library/react";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { theme } from "../../theme.js";
import { PinnedTag } from "./PinnedTag.jsx";
import { PriorityBadge } from "./PriorityBadge.jsx";

describe("PriorityBadge", () => {
  it.each([
    ["URGENT", "Urgent"],
    ["HIGH", "High"],
    ["NORMAL", "Normal"],
  ])("shows %s as %s in its approved colours", (priority, label) => {
    renderWithProviders(<PriorityBadge priority={priority} />);

    const badge = screen.getByText(label).closest("[data-priority]");
    const { background, color } = theme.other.priorityTags[priority];
    expect(badge).toHaveAttribute("data-priority", priority);
    expect(badge).toHaveStyle({ background, color });
  });
});

describe("PinnedTag", () => {
  it("reads Pinned first by default and accepts another label", () => {
    renderWithProviders(
      <>
        <PinnedTag />
        <PinnedTag label="Pinned first - ignored by optimal" />
      </>,
    );

    expect(screen.getByText("Pinned first")).toBeInTheDocument();
    expect(
      screen.getByText("Pinned first - ignored by optimal"),
    ).toBeInTheDocument();
  });
});

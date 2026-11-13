import { Button } from "@mantine/core";
import { screen } from "@testing-library/react";
import { ApiError } from "../../api/client.js";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { EmptyState } from "./EmptyState.jsx";
import { ErrorState } from "./ErrorState.jsx";
import { LoadingState } from "./LoadingState.jsx";

describe("LoadingState", () => {
  it("announces loading through a polite live region", () => {
    renderWithProviders(<LoadingState label="Loading trucks…" />);

    const status = screen.getByRole("status");
    expect(status).toHaveTextContent("Loading trucks…");
    expect(status).toHaveAttribute("aria-live", "polite");
  });
});

describe("ErrorState", () => {
  it("shows the ApiError detail and retries on click", async () => {
    const onRetry = vi.fn();
    const { user } = renderWithProviders(
      <ErrorState
        title="Could not load trucks"
        error={
          new ApiError({ status: 0, code: "NETWORK", detail: "Server down." })
        }
        onRetry={onRetry}
      />,
    );

    const alert = screen.getByRole("alert");
    expect(alert).toHaveTextContent("Could not load trucks");
    expect(alert).toHaveTextContent("Server down.");

    await user.click(screen.getByRole("button", { name: "Retry" }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it("hides the text of unexpected errors and has no Retry without a handler", () => {
    renderWithProviders(<ErrorState error={new TypeError("x.map")} />);

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Something went wrong. Please try again.",
    );
    expect(screen.queryByText(/x\.map/)).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Retry" }),
    ).not.toBeInTheDocument();
  });
});

describe("EmptyState", () => {
  it("shows the title, message and action", () => {
    renderWithProviders(
      <EmptyState
        title="No trucks yet"
        message="Add your first truck."
        action={<Button>Add truck</Button>}
      />,
    );

    expect(
      screen.getByRole("heading", { name: "No trucks yet" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Add your first truck.")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Add truck" }),
    ).toBeInTheDocument();
  });
});

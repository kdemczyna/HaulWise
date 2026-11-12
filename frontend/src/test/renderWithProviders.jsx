import { MantineProvider } from "@mantine/core";
import { render } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { cssVariablesResolver, theme } from "../theme.js";

/** Renders `ui` inside the app's providers (Mantine in test mode + a router at `route`). */
export function renderWithProviders(ui, { route = "/" } = {}) {
  const user = userEvent.setup();
  const result = render(
    <MantineProvider
      theme={theme}
      cssVariablesResolver={cssVariablesResolver}
      env="test"
    >
      <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
    </MantineProvider>,
  );
  return { user, ...result };
}

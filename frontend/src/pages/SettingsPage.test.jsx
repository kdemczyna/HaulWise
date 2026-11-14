import { screen, waitFor } from "@testing-library/react";
import { ApiError } from "../api/client.js";
import { getSettings, updateSettings } from "../api/settings.js";
import { renderWithProviders } from "../test/renderWithProviders.jsx";
import { SettingsPage } from "./SettingsPage.jsx";

vi.mock("../api/settings.js");

const SETTINGS = {
  dieselPricePlnPerLitre: 6.2,
  averageSpeedKmh: 55,
  roadFactor: 1.3,
  serviceMinutesPerStop: 30,
  urgentWeightPlnPerHour: 150,
  highWeightPlnPerHour: 80,
  complianceEnabled: true,
  maxContinuousDrivingMinutes: 270,
  breakMinutes: 45,
  maxDailyDrivingMinutes: 540,
  consolidationFillThresholdPct: 70,
};

describe("SettingsPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    getSettings.mockResolvedValue(SETTINGS);
  });

  it("loads and shows the current settings", async () => {
    renderWithProviders(<SettingsPage />);

    expect(
      await screen.findByRole("textbox", { name: /Diesel price/ }),
    ).toHaveValue("6.2");
    expect(screen.getByRole("textbox", { name: /Urgent weight/ })).toHaveValue(
      "150",
    );
    expect(screen.getByRole("switch")).toBeChecked();
    expect(screen.getByRole("textbox", { name: /Fill threshold/ })).toHaveValue(
      "70",
    );
  });

  it("disables the compliance and consolidation fields, which aren't wired to the planner yet", async () => {
    renderWithProviders(<SettingsPage />);

    expect(await screen.findByRole("switch")).toBeDisabled();
    expect(
      screen.getByRole("textbox", { name: /Max continuous driving/ }),
    ).toBeDisabled();
    expect(
      screen.getByRole("textbox", { name: /Break length/ }),
    ).toBeDisabled();
    expect(
      screen.getByRole("textbox", { name: /Max daily driving/ }),
    ).toBeDisabled();
    expect(
      screen.getByRole("textbox", { name: /Fill threshold/ }),
    ).toBeDisabled();
    expect(screen.getAllByText(/Not used yet/)).toHaveLength(2);
    // Everything else stays editable.
    expect(
      screen.getByRole("textbox", { name: /Diesel price/ }),
    ).not.toBeDisabled();
  });

  it("shows a load error and retries", async () => {
    getSettings
      .mockRejectedValueOnce(
        new ApiError({ status: 500, code: "INTERNAL_ERROR", detail: "Boom." }),
      )
      .mockResolvedValueOnce(SETTINGS);
    const { user } = renderWithProviders(<SettingsPage />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load settings",
    );
    await user.click(screen.getByRole("button", { name: "Retry" }));

    expect(
      await screen.findByRole("textbox", { name: /Diesel price/ }),
    ).toHaveValue("6.2");
  });

  it("checks the backend ranges on the client", async () => {
    const { user } = renderWithProviders(<SettingsPage />);
    const diesel = await screen.findByRole("textbox", {
      name: /Diesel price/,
    });

    await user.clear(diesel);
    await user.type(diesel, "0");
    await user.click(screen.getByRole("button", { name: "Save settings" }));

    expect(diesel).toHaveAccessibleDescription(
      /Diesel price must be greater than 0 and at most 50/,
    );
    expect(updateSettings).not.toHaveBeenCalled();
  });

  it("checks the urgent/high priority cross-field rule on the client", async () => {
    const { user } = renderWithProviders(<SettingsPage />);
    const urgent = await screen.findByRole("textbox", {
      name: /Urgent weight/,
    });

    await user.clear(urgent);
    await user.type(urgent, "10");
    await user.click(screen.getByRole("button", { name: "Save settings" }));

    expect(urgent).toHaveAccessibleDescription(
      /Urgent weight must be greater than or equal to high weight/,
    );
    expect(updateSettings).not.toHaveBeenCalled();
  });

  it("saves the settings and shows a success notification", async () => {
    updateSettings.mockResolvedValue({
      ...SETTINGS,
      dieselPricePlnPerLitre: 6.5,
    });
    const { user } = renderWithProviders(<SettingsPage />);
    const diesel = await screen.findByRole("textbox", {
      name: /Diesel price/,
    });

    await user.clear(diesel);
    await user.type(diesel, "6.5");
    await user.click(screen.getByRole("button", { name: "Save settings" }));

    expect(updateSettings).toHaveBeenCalledWith({
      ...SETTINGS,
      dieselPricePlnPerLitre: 6.5,
    });
    expect(await screen.findByText("Settings saved")).toBeInTheDocument();
  });

  it("shows a server field error on the matching field", async () => {
    updateSettings.mockRejectedValueOnce(
      new ApiError({
        status: 400,
        code: "VALIDATION_FAILED",
        detail: "Request has 1 invalid field.",
        fieldErrors: {
          maxContinuousDrivingMinutes:
            "must be less than or equal to maxDailyDrivingMinutes",
        },
      }),
    );
    const { user } = renderWithProviders(<SettingsPage />);
    await screen.findByRole("textbox", { name: /Diesel price/ });

    await user.click(screen.getByRole("button", { name: "Save settings" }));

    await waitFor(() =>
      expect(
        screen.getByRole("textbox", { name: /Max continuous driving/ }),
      ).toHaveAccessibleDescription(
        /must be less than or equal to maxDailyDrivingMinutes/,
      ),
    );
  });
});

import { getSettings, updateSettings } from "../api/settings.js";
import { ErrorState } from "../components/common/ErrorState.jsx";
import { LoadingState } from "../components/common/LoadingState.jsx";
import { PageHeader } from "../components/common/PageHeader.jsx";
import { SettingsForm } from "../components/forms/SettingsForm.jsx";
import { useAsync } from "../hooks/useAsync.js";
import { notifySuccess } from "../utils/notify.js";

export function SettingsPage() {
  const settings = useAsync(getSettings);

  async function save(request) {
    const saved = await updateSettings(request);
    settings.reload();
    notifySuccess("Settings saved");
    return saved;
  }

  return (
    <>
      <PageHeader
        title="Settings"
        description="Fuel price, speeds, service time, priority weights and driving-time limits used by the planner."
      />
      {settings.error ? (
        <ErrorState
          title="Could not load settings"
          error={settings.error}
          onRetry={settings.reload}
        />
      ) : !settings.data ? (
        <LoadingState label="Loading settings…" rows={6} />
      ) : (
        <SettingsForm settings={settings.data} onSave={save} />
      )}
    </>
  );
}

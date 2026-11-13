import { EmptyState } from "../components/common/EmptyState.jsx";
import { PageHeader } from "../components/common/PageHeader.jsx";

export function SettingsPage() {
  return (
    <>
      <PageHeader
        title="Settings"
        description="Fuel price, speeds, service time and priority weights used by the planner."
      />
      <EmptyState
        title="Not editable here yet"
        message="The planner uses the stored settings (Polish defaults). Until this page has its form, change them with PUT /api/settings (see the API docs)."
      />
    </>
  );
}

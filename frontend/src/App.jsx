import { lazy, Suspense } from "react";
import { Route, Routes } from "react-router";
import { AppLayout } from "./components/common/AppLayout.jsx";
import { LoadingState } from "./components/common/LoadingState.jsx";
import { NotFoundPage } from "./pages/NotFoundPage.jsx";
import { PlanPage } from "./pages/PlanPage.jsx";
import { SettingsPage } from "./pages/SettingsPage.jsx";
import { ShipmentsPage } from "./pages/ShipmentsPage.jsx";
import { TrucksPage } from "./pages/TrucksPage.jsx";
import { WarehousesPage } from "./pages/WarehousesPage.jsx";

// Lazy: keeps leaflet / react-leaflet out of the main bundle (ARCHITECTURE section 12, T-16).
const PlanResultPage = lazy(() =>
  import("./pages/PlanResultPage.jsx").then((module) => ({
    default: module.PlanResultPage,
  })),
);

function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<PlanPage />} />
        <Route
          path="plans/:id"
          element={
            <Suspense fallback={<LoadingState label="Loading the plan…" />}>
              <PlanResultPage />
            </Suspense>
          }
        />
        <Route path="shipments" element={<ShipmentsPage />} />
        <Route path="trucks" element={<TrucksPage />} />
        <Route path="warehouses" element={<WarehousesPage />} />
        <Route path="settings" element={<SettingsPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

export default App;

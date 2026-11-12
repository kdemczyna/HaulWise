import { Route, Routes } from "react-router";
import { AppLayout } from "./components/common/AppLayout.jsx";
import { NotFoundPage } from "./pages/NotFoundPage.jsx";
import { PlanPage } from "./pages/PlanPage.jsx";
import { PlanResultPage } from "./pages/PlanResultPage.jsx";
import { SettingsPage } from "./pages/SettingsPage.jsx";
import { ShipmentsPage } from "./pages/ShipmentsPage.jsx";
import { TrucksPage } from "./pages/TrucksPage.jsx";
import { WarehousesPage } from "./pages/WarehousesPage.jsx";

function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<PlanPage />} />
        <Route path="plans/:id" element={<PlanResultPage />} />
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

import { Routes, Route, Navigate } from 'react-router-dom'
import AppLayout from './components/layout/AppLayout'
import ProtectedRoute from './components/layout/ProtectedRoute'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import OrdersPage from './pages/OrdersPage'
import OrderDetailPage from './pages/OrderDetailPage'
import InventoryPage from './pages/InventoryPage'
import FulfillmentPage from './pages/FulfillmentPage'
import PickingPage from './pages/PickingPage'
import PackingPage from './pages/PackingPage'
import ShippingPage from './pages/ShippingPage'
import ReturnsPage from './pages/ReturnsPage'
import B2BPortalPage from './pages/B2BPortalPage'
import AnalyticsPage from './pages/AnalyticsPage'
import AiPage from './pages/AiPage'
import AiPlatformPage from './pages/AiPlatformPage'
import AiExperimentsPage from './pages/AiExperimentsPage'
import CarriersPage from './pages/CarriersPage'
import CarrierRateShoppingPage from './pages/CarrierRateShoppingPage'
import EdiAutomationPage from './pages/EdiAutomationPage'
import SettingsPage from './pages/SettingsPage'
import RoutingRulesPage from './pages/RoutingRulesPage'
import OrderRoutingPage from './pages/OrderRoutingPage'
import EmailOrderParsingPage from './pages/EmailOrderParsingPage'
import InventoryReceivingPage from './pages/InventoryReceivingPage'
import CycleCountPage from './pages/CycleCountPage'
import BigCommercePage from './pages/BigCommercePage'
import IntegrationStoresPage from './pages/IntegrationStoresPage'
import IntegrationHubPage from './pages/IntegrationHubPage'
import ImportExportCenter from './pages/ImportExportCenter'
import CustomersPage from './pages/CustomersPage'
import ProductsPage from './pages/ProductsPage'
import StoresPage from './pages/StoresPage'
import AuditPage from './pages/AuditPage'
import NotificationsCenter from './pages/NotificationsCenter'
import WarehousePage from './pages/WarehousePage'
import ProcurementPage from './pages/ProcurementPage'
import InvoicingPage from './pages/InvoicingPage'
import WorkflowsPage from './pages/WorkflowsPage'
import DocumentsPage from './pages/DocumentsPage'
import UsersPage from './pages/UsersPage'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="orders" element={<OrdersPage />} />
          <Route path="orders/:id" element={<OrderDetailPage />} />
          <Route path="inventory" element={<InventoryPage />} />
          <Route path="inventory/receiving" element={<InventoryReceivingPage />} />
          <Route path="inventory/cycle-counts" element={<CycleCountPage />} />
          <Route path="routing-rules" element={<RoutingRulesPage />} />
          <Route path="order-routing" element={<OrderRoutingPage />} />
          <Route path="integrations/bigcommerce" element={<BigCommercePage />} />
          <Route path="integrations/stores" element={<IntegrationStoresPage />} />
          <Route path="integrations" element={<IntegrationStoresPage />} />
          <Route path="integration-hub" element={<IntegrationHubPage />} />
          <Route path="import-export" element={<ImportExportCenter />} />
          <Route path="notifications" element={<NotificationsCenter />} />
          <Route path="warehouse" element={<WarehousePage />} />
          <Route path="procurement/*" element={<ProcurementPage />} />
          <Route path="invoices" element={<InvoicingPage />} />
          <Route path="workflows" element={<WorkflowsPage />} />
          <Route path="documents" element={<DocumentsPage />} />
          <Route path="users" element={<UsersPage />} />
          <Route path="picking" element={<PickingPage />} />
          <Route path="packing" element={<PackingPage />} />
          <Route path="shipping" element={<ShippingPage />} />
          <Route path="customers" element={<CustomersPage />} />
          <Route path="products" element={<ProductsPage />} />
          <Route path="stores" element={<StoresPage />} />
          <Route path="audit" element={<AuditPage />} />
          <Route path="fulfillment" element={<FulfillmentPage />} />
          <Route path="returns" element={<ReturnsPage />} />
          <Route path="b2b-portal" element={<B2BPortalPage />} />
          <Route path="carriers" element={<CarriersPage />} />
          <Route path="rate-shopping" element={<CarrierRateShoppingPage />} />
          <Route path="edi" element={<EdiAutomationPage />} />
          <Route path="email-parser" element={<EmailOrderParsingPage />} />
          <Route path="analytics" element={<AnalyticsPage />} />
          <Route path="ai" element={<AiPage />} />
          <Route path="ai-platform/*" element={<AiPlatformPage />} />
          <Route path="experiments" element={<AiExperimentsPage />} />
          <Route path="settings" element={<SettingsPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Route>
    </Routes>
  )
}

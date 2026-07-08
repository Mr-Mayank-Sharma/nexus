import { Routes, Route } from 'react-router-dom'
import AppLayout from './components/layout/AppLayout'
import ErrorBoundary from './components/layout/ErrorBoundary'
import NotFoundPage from './pages/NotFoundPage'
import ProtectedRoute from './components/layout/ProtectedRoute'
import LoginPage from './pages/LoginPage'
import LaunchPadPage from './pages/LaunchPadPage'
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
import BOPISPage from './pages/BOPISPage'
import AnalyticsPage from './pages/AnalyticsPage'
import AiPage from './pages/AiPage'
import AiPlatformPage from './pages/AiPlatformPage'
import AiExperimentsPage from './pages/AiExperimentsPage'
import AiBriefingPage from './pages/AiBriefingPage'
import AiOrderRoutingPage from './pages/AiOrderRoutingPage'
import AiPackingPage from './pages/AiPackingPage'
import AiLoadingPage from './pages/AiLoadingPage'
import AiAuditTrailPage from './pages/AiAuditTrailPage'
import AiForecastingPage from './pages/AiForecastingPage'
import AmazonIntegrationPage from './pages/AmazonIntegrationPage'
import EbayIntegrationPage from './pages/EbayIntegrationPage'
import WalmartIntegrationPage from './pages/WalmartIntegrationPage'
import IntegrationMarketplacePage from './pages/IntegrationMarketplacePage'
import InventoryEnhancedPage from './pages/InventoryEnhancedPage'
import WavePlanningPage from './pages/WavePlanningPage'
import LaborManagementPage from './pages/LaborManagementPage'
import LabelPrintingPage from './pages/LabelPrintingPage'
import ManifestPage from './pages/ManifestPage'
import ReportBuilderPage from './pages/ReportBuilderPage'
import ReturnsEnhancedPage from './pages/ReturnsEnhancedPage'
import PaymentsPage from './pages/PaymentsPage'
import CreateOrderPage from './pages/CreateOrderPage'
import CarriersPage from './pages/CarriersPage'
import CarrierRateShoppingPage from './pages/CarrierRateShoppingPage'
import EdiAutomationPage from './pages/EdiAutomationPage'
import SettingsPage from './pages/SettingsPage'
import RoutingRulesPage from './pages/RoutingRulesPage'
import OrderRoutingPage from './pages/OrderRoutingPage'
import EmailOrderParsingPage from './pages/EmailOrderParsingPage'
import PreOrdersPage from './pages/PreOrdersPage'
import ATPRulesPage from './pages/ATPRulesPage'
import TaskQueuesPage from './pages/TaskQueuesPage'
import WarehouseDashboardPage from './pages/WarehouseDashboardPage'
import PackerScreen from './pages/PackerScreen'
import LoaderScreen from './pages/LoaderScreen'
import StoreDashboardPage from './pages/StoreDashboardPage'
import BopisOwnerPage from './pages/BopisOwnerPage'
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
    <ErrorBoundary>
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route index element={<LaunchPadPage />} />
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="orders" element={<OrdersPage />} />
          <Route path="orders/new" element={<CreateOrderPage />} />
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
          <Route path="warehouse-dashboard" element={<WarehouseDashboardPage />} />
          <Route path="packer" element={<PackerScreen />} />
          <Route path="loader" element={<LoaderScreen />} />
          <Route path="store-dashboard" element={<StoreDashboardPage />} />
          <Route path="bopis-owner" element={<BopisOwnerPage />} />
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
          <Route path="bopis" element={<BOPISPage />} />
          <Route path="pre-orders" element={<PreOrdersPage />} />
          <Route path="atp-rules" element={<ATPRulesPage />} />
          <Route path="task-queues" element={<TaskQueuesPage />} />
          <Route path="carriers" element={<CarriersPage />} />
          <Route path="rate-shopping" element={<CarrierRateShoppingPage />} />
          <Route path="edi" element={<EdiAutomationPage />} />
          <Route path="email-parser" element={<EmailOrderParsingPage />} />
          <Route path="analytics" element={<AnalyticsPage />} />
          <Route path="ai" element={<AiPage />} />
          <Route path="ai-platform/*" element={<AiPlatformPage />} />
          <Route path="ai-briefing" element={<AiBriefingPage />} />
          <Route path="ai-routing" element={<AiOrderRoutingPage />} />
          <Route path="ai-packing" element={<AiPackingPage />} />
          <Route path="ai-loading" element={<AiLoadingPage />} />
          <Route path="ai-audit" element={<AiAuditTrailPage />} />
          <Route path="ai-forecasting" element={<AiForecastingPage />} />
          <Route path="experiments" element={<AiExperimentsPage />} />
          <Route path="settings" element={<SettingsPage />} />
          <Route path="inventory/enhanced" element={<InventoryEnhancedPage />} />
          <Route path="integrations/amazon" element={<AmazonIntegrationPage />} />
          <Route path="integrations/ebay" element={<EbayIntegrationPage />} />
          <Route path="integrations/walmart" element={<WalmartIntegrationPage />} />
          <Route path="integrations/marketplace" element={<IntegrationMarketplacePage />} />
          <Route path="wave-planning" element={<WavePlanningPage />} />
          <Route path="labor-management" element={<LaborManagementPage />} />
          <Route path="label-printing" element={<LabelPrintingPage />} />
          <Route path="manifest" element={<ManifestPage />} />
          <Route path="report-builder" element={<ReportBuilderPage />} />
          <Route path="returns-enhanced" element={<ReturnsEnhancedPage />} />
          <Route path="payments" element={<PaymentsPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
    </ErrorBoundary>
  )
}

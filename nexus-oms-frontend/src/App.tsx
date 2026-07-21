import { lazy, Suspense } from 'react'
import { Routes, Route } from 'react-router-dom'
import AppLayout from './components/layout/AppLayout'
import ErrorBoundary from './components/layout/ErrorBoundary'
import NotFoundPage from './pages/NotFoundPage'
import ProtectedRoute from './components/layout/ProtectedRoute'
import LoginPage from './pages/LoginPage'

const LaunchPadPage = lazy(() => import('./pages/LaunchPadPage'))
const DashboardPage = lazy(() => import('./pages/DashboardPage'))
const OrdersPage = lazy(() => import('./pages/OrdersPage'))
const OrderDetailPage = lazy(() => import('./pages/OrderDetailPage'))
const InventoryPage = lazy(() => import('./pages/InventoryPage'))
const FulfillmentPage = lazy(() => import('./pages/FulfillmentPage'))
const PickingPage = lazy(() => import('./pages/PickingPage'))
const PackingPage = lazy(() => import('./pages/PackingPage'))
const ShippingPage = lazy(() => import('./pages/ShippingPage'))
const ReturnsPage = lazy(() => import('./pages/ReturnsPage'))
const B2BPortalPage = lazy(() => import('./pages/B2BPortalPage'))
const BOPISPage = lazy(() => import('./pages/BOPISPage'))
const AnalyticsPage = lazy(() => import('./pages/AnalyticsPage'))
const AiPage = lazy(() => import('./pages/AiPage'))
const AiPlatformPage = lazy(() => import('./pages/AiPlatformPage'))
const AiExperimentsPage = lazy(() => import('./pages/AiExperimentsPage'))
const AiBriefingPage = lazy(() => import('./pages/AiBriefingPage'))
const AiOrderRoutingPage = lazy(() => import('./pages/AiOrderRoutingPage'))
const AiPackingPage = lazy(() => import('./pages/AiPackingPage'))
const AiLoadingPage = lazy(() => import('./pages/AiLoadingPage'))
const AiAuditTrailPage = lazy(() => import('./pages/AiAuditTrailPage'))
const AiForecastingPage = lazy(() => import('./pages/AiForecastingPage'))
const AmazonIntegrationPage = lazy(() => import('./pages/AmazonIntegrationPage'))
const EbayIntegrationPage = lazy(() => import('./pages/EbayIntegrationPage'))
const WalmartIntegrationPage = lazy(() => import('./pages/WalmartIntegrationPage'))
const IntegrationMarketplacePage = lazy(() => import('./pages/IntegrationMarketplacePage'))
const InventoryEnhancedPage = lazy(() => import('./pages/InventoryEnhancedPage'))
const WavePlanningPage = lazy(() => import('./pages/WavePlanningPage'))
const LaborManagementPage = lazy(() => import('./pages/LaborManagementPage'))
const LabelPrintingPage = lazy(() => import('./pages/LabelPrintingPage'))
const ManifestPage = lazy(() => import('./pages/ManifestPage'))
const ReportBuilderPage = lazy(() => import('./pages/ReportBuilderPage'))
const ReturnsEnhancedPage = lazy(() => import('./pages/ReturnsEnhancedPage'))
const PaymentsPage = lazy(() => import('./pages/PaymentsPage'))
const SlottingOptimizationPage = lazy(() => import('./pages/SlottingOptimizationPage'))
const YardDockPage = lazy(() => import('./pages/YardDockPage'))
const AutomationSystemsPage = lazy(() => import('./pages/AutomationSystemsPage'))
const CreateOrderPage = lazy(() => import('./pages/CreateOrderPage'))
const CarriersPage = lazy(() => import('./pages/CarriersPage'))
const CarrierRateShoppingPage = lazy(() => import('./pages/CarrierRateShoppingPage'))
const EdiAutomationPage = lazy(() => import('./pages/EdiAutomationPage'))
const SettingsPage = lazy(() => import('./pages/SettingsPage'))
const RoutingRulesPage = lazy(() => import('./pages/RoutingRulesPage'))
const OrderRoutingPage = lazy(() => import('./pages/OrderRoutingPage'))
const EmailOrderParsingPage = lazy(() => import('./pages/EmailOrderParsingPage'))
const PreOrdersPage = lazy(() => import('./pages/PreOrdersPage'))
const ATPRulesPage = lazy(() => import('./pages/ATPRulesPage'))
const TaskQueuesPage = lazy(() => import('./pages/TaskQueuesPage'))
const WarehouseDashboardPage = lazy(() => import('./pages/WarehouseDashboardPage'))
const PackerScreen = lazy(() => import('./pages/PackerScreen'))
const LoaderScreen = lazy(() => import('./pages/LoaderScreen'))
const StoreDashboardPage = lazy(() => import('./pages/StoreDashboardPage'))
const BopisOwnerPage = lazy(() => import('./pages/BopisOwnerPage'))
const InventoryReceivingPage = lazy(() => import('./pages/InventoryReceivingPage'))
const CycleCountPage = lazy(() => import('./pages/CycleCountPage'))
const BigCommercePage = lazy(() => import('./pages/BigCommercePage'))
const IntegrationStoresPage = lazy(() => import('./pages/IntegrationStoresPage'))
const IntegrationHubPage = lazy(() => import('./pages/IntegrationHubPage'))
const ImportExportCenter = lazy(() => import('./pages/ImportExportCenter'))
const CustomersPage = lazy(() => import('./pages/CustomersPage'))
const ProductsPage = lazy(() => import('./pages/ProductsPage'))
const AuditPage = lazy(() => import('./pages/AuditPage'))
const NotificationsCenter = lazy(() => import('./pages/NotificationsCenter'))
const WarehousePage = lazy(() => import('./pages/WarehousePage'))
const ProcurementPage = lazy(() => import('./pages/ProcurementPage'))
const InvoicingPage = lazy(() => import('./pages/InvoicingPage'))
const WorkflowsPage = lazy(() => import('./pages/WorkflowsPage'))
const DocumentsPage = lazy(() => import('./pages/DocumentsPage'))
const UsersPage = lazy(() => import('./pages/UsersPage'))
const TransfersPage = lazy(() => import('./pages/TransfersPage'))
const BrokeringQueuePage = lazy(() => import('./pages/BrokeringQueuePage'))
const RejectionsPage = lazy(() => import('./pages/RejectionsPage'))
const PickersPage = lazy(() => import('./pages/PickersPage'))
const OrderApprovalsPage = lazy(() => import('./pages/OrderApprovalsPage'))
const FulfillmentLimitsPage = lazy(() => import('./pages/FulfillmentLimitsPage'))
const BopisAppPage = lazy(() => import('./pages/BopisAppPage'))
const ReplenishmentPage = lazy(() => import('./pages/ReplenishmentPage'))
const FreightAuditPage = lazy(() => import('./pages/FreightAuditPage'))

function PageLoader() {
  return (
    <div className="flex items-center justify-center h-64">
      <div className="animate-spin w-8 h-8 border-2 border-primary-500 border-t-transparent rounded-full" />
    </div>
  )
}

export default function App() {
  return (
    <ErrorBoundary>
    <Suspense fallback={<PageLoader />}>
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
          <Route path="stores" element={<IntegrationStoresPage />} />
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
          <Route path="slotting-optimization" element={<SlottingOptimizationPage />} />
          <Route path="yard-dock" element={<YardDockPage />} />
          <Route path="automation-systems" element={<AutomationSystemsPage />} />
          <Route path="label-printing" element={<LabelPrintingPage />} />
          <Route path="manifest" element={<ManifestPage />} />
          <Route path="report-builder" element={<ReportBuilderPage />} />
          <Route path="returns-enhanced" element={<ReturnsEnhancedPage />} />
          <Route path="payments" element={<PaymentsPage />} />
          <Route path="transfers" element={<TransfersPage />} />
          <Route path="brokering" element={<BrokeringQueuePage />} />
          <Route path="rejections" element={<RejectionsPage />} />
          <Route path="pickers" element={<PickersPage />} />
          <Route path="order-approvals" element={<OrderApprovalsPage />} />
          <Route path="fulfillment-limits" element={<FulfillmentLimitsPage />} />
          <Route path="bopis-app" element={<BopisAppPage />} />
          <Route path="replenishment" element={<ReplenishmentPage />} />
          <Route path="freight-audit" element={<FreightAuditPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
    </Suspense>
    </ErrorBoundary>
  )
}

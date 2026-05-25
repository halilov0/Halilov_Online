import { Routes, Route } from 'react-router-dom'
import { Layout } from './components/Layout'
import { RequireAdmin } from './components/RequireAdmin'
import { ToastHost } from './components/Toast'
import { LoginPage } from './pages/LoginPage'
import { DashboardPage } from './pages/DashboardPage'
import { ProductsPage } from './pages/ProductsPage'
import { CategoriesPage } from './pages/CategoriesPage'
import { CouponsPage } from './pages/CouponsPage'
import { OrdersPage } from './pages/OrdersPage'
import { OrderDetailPage } from './pages/OrderDetailPage'
import { MarketingPage } from './pages/MarketingPage'
import { UsersPage } from './pages/UsersPage'
import { AuditLogPage } from './pages/AuditLogPage'

function App() {
  return (
    <>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<RequireAdmin><Layout /></RequireAdmin>}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/products" element={<ProductsPage />} />
          <Route path="/categories" element={<CategoriesPage />} />
          <Route path="/coupons" element={<CouponsPage />} />
          <Route path="/orders" element={<OrdersPage />} />
          <Route path="/orders/:orderNumber" element={<OrderDetailPage />} />
          <Route path="/marketing" element={<MarketingPage />} />
          <Route path="/users" element={<UsersPage />} />
          <Route path="/audit-log" element={<AuditLogPage />} />
        </Route>
        <Route path="*" element={<div style={{ padding: '2rem' }}>404</div>} />
      </Routes>
      <ToastHost />
    </>
  )
}

export default App

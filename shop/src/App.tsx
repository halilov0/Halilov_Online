import { useEffect } from 'react'
import { Routes, Route, useLocation } from 'react-router-dom'
import { Header } from './components/Header'
import { ToastHost } from './components/Toast'
import { WhatsAppFab } from './components/WhatsAppFab'
import { CatalogPage } from './pages/CatalogPage'
import { ProductPage } from './pages/ProductPage'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { CartPage } from './pages/CartPage'
import { CheckoutPage } from './pages/CheckoutPage'
import { OrderConfirmationPage } from './pages/OrderConfirmationPage'
import { MockPaymentPage } from './pages/MockPaymentPage'
import { TrackOrderPage } from './pages/TrackOrderPage'
import { InvoicePage } from './pages/InvoicePage'
import { InfoPage } from './pages/InfoPage'
import { FavoritesPage } from './pages/FavoritesPage'
import { AccountPage } from './pages/AccountPage'
import { useAuth } from './auth/authStore'
import { useCart } from './cart/cartStore'
import { getToken } from './api'

function App() {
  const fetchMe = useAuth(s => s.fetchMe)
  const loc = useLocation()

  useEffect(() => {
    // Validate the persisted token, then hydrate the cart from the server so
    // changes made in other tabs/devices show up on this mount.
    ;(async () => {
      await fetchMe()
      if (getToken()) {
        await useCart.getState().loadFromRemote()
      }
    })()
  }, [fetchMe])

  // Auth pages render their own split layout (no global header)
  // Mock payment page mimics an external gateway, so we hide our chrome too.
  const hideHeader = loc.pathname === '/login'
    || loc.pathname === '/register'
    || loc.pathname.startsWith('/payment/')
    || loc.pathname.startsWith('/invoice/')

  return (
    <>
      {!hideHeader && <Header />}
      <Routes>
        <Route path="/" element={<CatalogPage />} />
        <Route path="/p/:slug" element={<ProductPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/cart" element={<CartPage />} />
        <Route path="/checkout" element={<CheckoutPage />} />
        <Route path="/orders/:orderNumber" element={<OrderConfirmationPage />} />
        <Route path="/payment/mock" element={<MockPaymentPage />} />
        <Route path="/track" element={<TrackOrderPage />} />
        <Route path="/invoice/:orderNumber" element={<InvoicePage />} />
        <Route path="/about"    element={<InfoPage slug="about" />} />
        <Route path="/shipping" element={<InfoPage slug="shipping" />} />
        <Route path="/returns"  element={<InfoPage slug="returns" />} />
        <Route path="/faq"      element={<InfoPage slug="faq" />} />
        <Route path="/contact"  element={<InfoPage slug="contact" />} />
        <Route path="/terms"         element={<InfoPage slug="terms" />} />
        <Route path="/privacy"       element={<InfoPage slug="privacy" />} />
        <Route path="/accessibility" element={<InfoPage slug="accessibility" />} />
        <Route path="/favorites" element={<FavoritesPage />} />
        <Route path="/account" element={<AccountPage />} />
        <Route path="*" element={<div className="hm-page"><h1>404</h1></div>} />
      </Routes>
      {!hideHeader && <WhatsAppFab />}
      <ToastHost />
    </>
  )
}

export default App

import { lazy, Suspense, type ReactNode } from 'react';
import { BrowserRouter, Route, Routes } from 'react-router';
import Layout from '../layouts/MainLayout';
import { FullPageSpinner, RequireAuth, RequireStaff } from '../routes/guards';
import LandingPage from '../features/catalogue/LandingPage';
import CataloguePage from '../features/catalogue/CataloguePage';
import BookDetailPage from '../features/catalogue/BookDetailPage';
import LoginPage from '../features/auth/LoginPage';
import RegisterPage from '../features/auth/RegisterPage';
import ForgotPasswordPage from '../features/auth/ForgotPasswordPage';
import { NotFoundPage } from '../components/StatusPages';

// Code-split: guests only download the public pages. Member pages (and PDF.js for the reader)
// load on first visit to those routes.
const MePage = lazy(() => import('../features/member/MePage'));
const ProfilePage = lazy(() => import('../features/member/ProfilePage'));
const RequestsPage = lazy(() => import('../features/member/RequestsPage'));
const ReaderPage = lazy(() => import('../features/reader/ReaderPage'));
const AdminRoutes = lazy(() => import('../features/admin/AdminRoutes'));

/** Signed-in route with a lazily loaded page. */
function Member({ children }: { children: ReactNode }) {
  return (
    <RequireAuth>
      <Suspense fallback={<FullPageSpinner />}>{children}</Suspense>
    </RequireAuth>
  );
}

// Routes follow docs/UI_FLOWS.md. Route guards are UX only; the API enforces every permission.
export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<LandingPage />} />
          <Route path="books" element={<CataloguePage />} />
          <Route path="books/:id" element={<BookDetailPage />} />

          <Route path="login" element={<LoginPage />} />
          <Route path="register" element={<RegisterPage />} />
          <Route path="forgot-password" element={<ForgotPasswordPage />} />

          <Route path="me" element={<Member><MePage /></Member>} />
          <Route path="me/profile" element={<Member><ProfilePage /></Member>} />
          <Route path="me/requests" element={<Member><RequestsPage /></Member>} />
          <Route path="read/:id" element={<Member><ReaderPage /></Member>} />

          <Route
            path="admin/*"
            element={
              <RequireStaff>
                <Suspense fallback={<FullPageSpinner />}>
                  <AdminRoutes />
                </Suspense>
              </RequireStaff>
            }
          />

          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

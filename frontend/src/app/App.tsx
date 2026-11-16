import { lazy, Suspense, type ReactNode } from 'react';
import { BrowserRouter, Route, Routes } from 'react-router';
import Layout from '../layouts/MainLayout';
import { FullPageSpinner, RequireAdmin, RequireAuth } from '../routes/guards';
import LandingPage from '../features/catalogue/LandingPage';
import CataloguePage from '../features/catalogue/CataloguePage';
import BookDetailPage from '../features/catalogue/BookDetailPage';
import LoginPage from '../features/auth/LoginPage';
import RegisterPage from '../features/auth/RegisterPage';
import VerifyEmailPage from '../features/auth/VerifyEmailPage';
import ForgotPasswordPage from '../features/auth/ForgotPasswordPage';
import ResetPasswordPage from '../features/auth/ResetPasswordPage';
import { ComingSoonPage, NotFoundPage } from '../components/StatusPages';

// Code-split: guests only download the public pages. Member pages (and PDF.js for the reader)
// load on first visit to those routes.
const MePage = lazy(() => import('../features/member/MePage'));
const ProfilePage = lazy(() => import('../features/member/ProfilePage'));
const RequestsPage = lazy(() => import('../features/member/RequestsPage'));
const ReaderPage = lazy(() => import('../features/reader/ReaderPage'));

/** Signed-in route with a lazily loaded page. */
function Member({ children }: { children: ReactNode }) {
  return (
    <RequireAuth>
      <Suspense fallback={<FullPageSpinner />}>{children}</Suspense>
    </RequireAuth>
  );
}

// Routes follow docs/UI_FLOWS.md. Admin pages are not built yet and render a placeholder.
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
          <Route path="verify-email" element={<VerifyEmailPage />} />
          <Route path="forgot-password" element={<ForgotPasswordPage />} />
          <Route path="reset-password" element={<ResetPasswordPage />} />

          <Route path="me" element={<Member><MePage /></Member>} />
          <Route path="me/profile" element={<Member><ProfilePage /></Member>} />
          <Route path="me/requests" element={<Member><RequestsPage /></Member>} />
          <Route path="read/:id" element={<Member><ReaderPage /></Member>} />

          <Route path="admin/*" element={<RequireAdmin><ComingSoonPage title="Admin" /></RequireAdmin>} />

          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

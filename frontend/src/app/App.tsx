import { BrowserRouter, Route, Routes } from 'react-router';
import Layout from '../layouts/MainLayout';
import { RequireAdmin, RequireAuth } from '../routes/guards';
import LandingPage from '../features/catalogue/LandingPage';
import CataloguePage from '../features/catalogue/CataloguePage';
import BookDetailPage from '../features/catalogue/BookDetailPage';
import LoginPage from '../features/auth/LoginPage';
import RegisterPage from '../features/auth/RegisterPage';
import VerifyEmailPage from '../features/auth/VerifyEmailPage';
import ForgotPasswordPage from '../features/auth/ForgotPasswordPage';
import ResetPasswordPage from '../features/auth/ResetPasswordPage';
import MePage from '../features/member/MePage';
import { ComingSoonPage, NotFoundPage } from '../components/StatusPages';

// Routes follow docs/UI_FLOWS.md. Member/admin pages not built yet render a placeholder.
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

          <Route path="me" element={<RequireAuth><MePage /></RequireAuth>} />
          <Route path="me/profile" element={<RequireAuth><ComingSoonPage title="Profile" /></RequireAuth>} />
          <Route path="me/requests" element={<RequireAuth><ComingSoonPage title="My requests" /></RequireAuth>} />
          <Route path="read/:id" element={<RequireAuth><ComingSoonPage title="Reader" /></RequireAuth>} />

          <Route path="admin/*" element={<RequireAdmin><ComingSoonPage title="Admin" /></RequireAdmin>} />

          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

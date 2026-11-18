import type { ReactNode } from 'react';
import { Navigate, Route, Routes } from 'react-router';
import { NotFoundPage } from '../../components/StatusPages';
import ForbiddenPage from '../../components/ForbiddenPage';
import { useAuth } from '../../contexts/AuthContext';
import AdminLayout from './AdminLayout';
import DashboardPage from './DashboardPage';
import UsersPage from './UsersPage';
import UserDetailPage from './UserDetailPage';
import MembershipsPage from './MembershipsPage';
import BooksPage from './BooksPage';
import BookFormPage from './BookFormPage';
import CategoriesPage from './CategoriesPage';
import RequestsReviewPage from './RequestsReviewPage';
import AuditPage from './AuditPage';

/** Admin-only pages; publishers (who only manage their own books) get a 403 page. */
function AdminOnly({ children }: { children: ReactNode }) {
  const { isAdmin } = useAuth();
  return isAdmin ? <>{children}</> : <ForbiddenPage />;
}

/**
 * Everything under /admin (docs/UI_FLOWS.md). Loaded as one lazy chunk, behind RequireStaff.
 * Publishers land on the books pages; the rest is for administrators.
 */
export default function AdminRoutes() {
  const { isAdmin } = useAuth();
  return (
    <Routes>
      <Route element={<AdminLayout />}>
        <Route index element={isAdmin ? <DashboardPage /> : <Navigate to="books" replace />} />
        <Route path="users" element={<AdminOnly><UsersPage /></AdminOnly>} />
        <Route path="users/:id" element={<AdminOnly><UserDetailPage /></AdminOnly>} />
        <Route path="memberships" element={<AdminOnly><MembershipsPage /></AdminOnly>} />
        <Route path="books" element={<BooksPage />} />
        <Route path="books/new" element={<BookFormPage />} />
        <Route path="books/:id" element={<BookFormPage />} />
        <Route path="categories" element={<AdminOnly><CategoriesPage /></AdminOnly>} />
        <Route path="requests" element={<AdminOnly><RequestsReviewPage /></AdminOnly>} />
        <Route path="audit" element={<AdminOnly><AuditPage /></AdminOnly>} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

import { Route, Routes } from 'react-router';
import { NotFoundPage } from '../../components/StatusPages';
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

/** Everything under /admin (docs/UI_FLOWS.md). Loaded as one lazy chunk, behind RequireAdmin. */
export default function AdminRoutes() {
  return (
    <Routes>
      <Route element={<AdminLayout />}>
        <Route index element={<DashboardPage />} />
        <Route path="users" element={<UsersPage />} />
        <Route path="users/:id" element={<UserDetailPage />} />
        <Route path="memberships" element={<MembershipsPage />} />
        <Route path="books" element={<BooksPage />} />
        <Route path="books/new" element={<BookFormPage />} />
        <Route path="books/:id" element={<BookFormPage />} />
        <Route path="categories" element={<CategoriesPage />} />
        <Route path="requests" element={<RequestsReviewPage />} />
        <Route path="audit" element={<AuditPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

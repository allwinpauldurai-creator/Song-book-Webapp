import { api, downloadAuthenticated, upload, type UploadOptions } from './client';
import type { MembershipStatus, ModificationRequest, PageResponse, RequestStatus, Role } from '../../types/api';
import type {
  AccountStatus,
  AdminBookDetail,
  AdminBookSummary,
  AdminCategory,
  AdminMembership,
  AdminStats,
  AdminUserDetail,
  AdminUserSummary,
  AuditLogEntry,
  BookMetadata,
  BookStatus,
  BookVersion,
} from '../../types/admin';

// All endpoints require ADMIN (backend: /api/v1/admin/**). See docs/API_SPECIFICATION.md.

const enc = encodeURIComponent;

export interface Paging {
  page?: number;
  size?: number;
  sort?: string;
}

export const adminApi = {
  stats: () => api<AdminStats>('/admin/stats'),

  // ---- users
  users: (q: Paging & { q?: string; status?: AccountStatus | ''; role?: Role | '' }) =>
    api<PageResponse<AdminUserSummary>>('/admin/users', { query: { ...q } }),
  user: (id: string) => api<AdminUserDetail>(`/admin/users/${enc(id)}`),
  createUser: (body: {
    fullName: string;
    mobile: string;
    email?: string | null;
    password: string;
    roles: Role[];
    mobileVerified: boolean;
  }) =>
    api<AdminUserDetail>('/admin/users', { method: 'POST', body }),
  updateUser: (
    id: string,
    body: {
      fullName: string;
      mobile: string;
      mobileVerified: boolean;
      email?: string | null;
      accountStatus: AccountStatus;
      roles: Role[];
      version: number;
    },
  ) =>
    api<AdminUserDetail>(`/admin/users/${enc(id)}`, { method: 'PUT', body }),

  // ---- memberships
  memberships: (q: Paging & { userId?: string; status?: MembershipStatus | '' }) =>
    api<PageResponse<AdminMembership>>('/admin/memberships', { query: { ...q } }),
  createMembership: (body: { userId: string; startDate: string; endDate?: string | null; status: MembershipStatus; notes?: string | null }) =>
    api<AdminMembership>('/admin/memberships', { method: 'POST', body }),
  updateMembership: (
    id: string,
    body: { startDate: string; endDate?: string | null; status: MembershipStatus; notes?: string | null; version: number },
  ) => api<AdminMembership>(`/admin/memberships/${enc(id)}`, { method: 'PUT', body }),

  // ---- categories
  categories: () => api<AdminCategory[]>('/admin/categories'),
  createCategory: (body: { name: string; description?: string | null }) =>
    api<AdminCategory>('/admin/categories', { method: 'POST', body }),
  updateCategory: (id: string, body: { name: string; description?: string | null; active: boolean; version: number }) =>
    api<AdminCategory>(`/admin/categories/${enc(id)}`, { method: 'PUT', body }),

  // ---- books
  books: (q: Paging & { q?: string; status?: BookStatus | ''; categoryId?: string }) =>
    api<PageResponse<AdminBookSummary>>('/admin/books', { query: { ...q } }),
  book: (id: string) => api<AdminBookDetail>(`/admin/books/${enc(id)}`),
  createBook: (metadata: BookMetadata, pdf: File, cover: File | null, opts?: UploadOptions) => {
    const form = new FormData();
    form.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }));
    form.append('pdf', pdf);
    if (cover) form.append('cover', cover);
    return upload<AdminBookDetail>('/admin/books', form, opts);
  },
  updateBook: (id: string, body: BookMetadata & { version: number }) =>
    api<AdminBookDetail>(`/admin/books/${enc(id)}`, { method: 'PUT', body }),
  replaceCover: (id: string, cover: File, opts?: UploadOptions) => {
    const form = new FormData();
    form.append('cover', cover);
    return upload<AdminBookDetail>(`/admin/books/${enc(id)}/cover`, form, { ...opts, method: 'PUT' });
  },
  versions: (id: string) => api<BookVersion[]>(`/admin/books/${enc(id)}/versions`),
  addVersion: (id: string, pdf: File, changeNote: string, opts?: UploadOptions) => {
    const form = new FormData();
    form.append('pdf', pdf);
    // changeNote is a request parameter, not a multipart part (AdminBookController#addVersion).
    return upload<BookVersion>(`/admin/books/${enc(id)}/versions`, form, { ...opts, query: { changeNote: changeNote || undefined } });
  },
  downloadVersion: (id: string, version: BookVersion) =>
    downloadAuthenticated(`/admin/books/${enc(id)}/versions/${enc(version.id)}/content`, version.originalFilename || 'book.pdf'),
  archiveBook: (id: string) => api<void>(`/admin/books/${enc(id)}/archive`, { method: 'POST' }),

  // ---- modification requests
  requests: (q: Paging & { status?: RequestStatus | '' }) =>
    api<PageResponse<ModificationRequest>>('/admin/modification-requests', { query: { ...q } }),
  reviewRequest: (id: string, body: { status: 'APPROVED' | 'REJECTED'; reviewNote?: string | null; version: number }) =>
    api<ModificationRequest>(`/admin/modification-requests/${enc(id)}`, { method: 'PUT', body }),

  // ---- audit
  audit: (q: Paging & { actorId?: string; action?: string; entityType?: string; entityId?: string; from?: string; to?: string }) =>
    api<PageResponse<AuditLogEntry>>('/admin/audit-logs', { query: { ...q } }),
};

export const adminKeys = {
  all: ['admin'] as const,
  stats: ['admin', 'stats'] as const,
  users: (q: object) => ['admin', 'users', q] as const,
  user: (id: string) => ['admin', 'user', id] as const,
  memberships: (q: object) => ['admin', 'memberships', q] as const,
  categories: ['admin', 'categories'] as const,
  books: (q: object) => ['admin', 'books', q] as const,
  book: (id: string) => ['admin', 'book', id] as const,
  versions: (id: string) => ['admin', 'versions', id] as const,
  requests: (q: object) => ['admin', 'requests', q] as const,
  audit: (q: object) => ['admin', 'audit', q] as const,
};

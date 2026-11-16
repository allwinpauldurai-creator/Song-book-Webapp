import { api } from './client';
import type {
  AccessTicket,
  BookDetail,
  BookSummary,
  Category,
  LoginResponse,
  Me,
  MessageResponse,
  PageResponse,
} from './types';

// ---------------------------------------------------------------- auth (public)

export const authApi = {
  login: (email: string, password: string) =>
    api<LoginResponse>('/auth/login', { method: 'POST', body: { email, password }, auth: false }),
  logout: () => api<void>('/auth/logout', { method: 'POST', auth: false }),
  register: (fullName: string, email: string, password: string) =>
    api<MessageResponse>('/auth/register', { method: 'POST', body: { fullName, email, password }, auth: false }),
  verifyEmail: (token: string) =>
    api<void>('/auth/verify-email', { method: 'POST', body: { token }, auth: false }),
  resendVerification: (email: string) =>
    api<MessageResponse>('/auth/resend-verification', { method: 'POST', body: { email }, auth: false }),
  forgotPassword: (email: string) =>
    api<MessageResponse>('/auth/forgot-password', { method: 'POST', body: { email }, auth: false }),
  resetPassword: (token: string, newPassword: string) =>
    api<void>('/auth/reset-password', { method: 'POST', body: { token, newPassword }, auth: false }),
};

export const usersApi = {
  me: () => api<Me>('/users/me'),
};

// ---------------------------------------------------------------- catalogue (public)

export type BookSort = 'title,asc' | 'updatedAt,desc' | 'effectiveDate,desc';

export interface BookQuery {
  q?: string;
  categoryId?: string;
  page?: number;
  size?: number;
  sort?: BookSort;
}

export const catalogueApi = {
  categories: () => api<Category[]>('/categories', { auth: false }),
  books: (query: BookQuery, signal?: AbortSignal) =>
    api<PageResponse<BookSummary>>('/books', { query: { ...query }, auth: false, signal }),
  book: (id: string) => api<BookDetail>(`/books/${encodeURIComponent(id)}`, { auth: false }),
};

// ---------------------------------------------------------------- member PDF access

export const readerApi = {
  downloadAccess: (bookId: string) => api<AccessTicket>(`/books/${encodeURIComponent(bookId)}/download-access`),
  readAccess: (bookId: string) => api<AccessTicket>(`/books/${encodeURIComponent(bookId)}/read-access`),
};

// ---------------------------------------------------------------- query keys

export const queryKeys = {
  me: ['me'] as const,
  categories: ['categories'] as const,
  books: (q: BookQuery) => ['books', q] as const,
  book: (id: string) => ['book', id] as const,
};

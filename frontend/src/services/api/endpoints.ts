import { api, ApiError } from './client';
import type {
  AccessTicket,
  HistoryItem,
  ModificationRequest,
  ReadingProgress,
  RequestType,
  BookDetail,
  BookSummary,
  Category,
  LoginResponse,
  Me,
  MessageResponse,
  PageResponse,
} from '../../types/api';

// ---------------------------------------------------------------- auth (public)

// Mobile numbers are sent as E.164 (see utils/mobile.ts); SMS codes are six digits.
export const authApi = {
  login: (mobile: string, password: string) =>
    api<LoginResponse>('/auth/login', { method: 'POST', body: { mobile, password }, auth: false }),
  logout: () => api<void>('/auth/logout', { method: 'POST', auth: false }),
  register: (body: { fullName: string; mobile: string; email?: string | null; password: string }) =>
    api<MessageResponse>('/auth/register', { method: 'POST', body, auth: false }),
  verifyMobile: (mobile: string, code: string) =>
    api<void>('/auth/verify-mobile', { method: 'POST', body: { mobile, code }, auth: false }),
  resendVerification: (mobile: string) =>
    api<MessageResponse>('/auth/resend-verification', { method: 'POST', body: { mobile }, auth: false }),
  forgotPassword: (mobile: string) =>
    api<MessageResponse>('/auth/forgot-password', { method: 'POST', body: { mobile }, auth: false }),
  resetPassword: (mobile: string, code: string, newPassword: string) =>
    api<void>('/auth/reset-password', { method: 'POST', body: { mobile, code, newPassword }, auth: false }),
};

export const usersApi = {
  me: () => api<Me>('/users/me'),
  updateProfile: (fullName: string, email: string | null) =>
    api<Me>('/users/me', { method: 'PUT', body: { fullName, email } }),
  /** Revokes every session of the user on success (the caller should log out). */
  changePassword: (currentPassword: string, newPassword: string) =>
    api<void>('/users/me/password', { method: 'PUT', body: { currentPassword, newPassword } }),
  /** Step 1: sends an SMS code to the new number. */
  requestMobileChange: (newMobile: string, currentPassword: string) =>
    api<MessageResponse>('/users/me/mobile', { method: 'POST', body: { newMobile, currentPassword } }),
  /** Step 2: switches to the new number. */
  confirmMobileChange: (code: string) => api<Me>('/users/me/mobile/confirm', { method: 'POST', body: { code } }),
};

// ---------------------------------------------------------------- member area

export const memberApi = {
  history: (page = 0, size = 10) => api<PageResponse<HistoryItem>>('/members/progress', { query: { page, size } }),
  requests: (page = 0, size = 10) =>
    api<PageResponse<ModificationRequest>>('/members/modification-requests', { query: { page, size } }),
  createRequest: (body: { bookId?: string | null; requestType: RequestType; description: string }) =>
    api<ModificationRequest>('/modification-requests', { method: 'POST', body }),
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
  /** Last page read, or null when the member has not opened this book yet (404). */
  progress: async (bookId: string): Promise<ReadingProgress | null> => {
    try {
      return await api<ReadingProgress>(`/members/books/${encodeURIComponent(bookId)}/progress`);
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) return null;
      throw e;
    }
  },
  saveProgress: (bookId: string, lastPage: number) =>
    api<ReadingProgress>(`/members/books/${encodeURIComponent(bookId)}/progress`, { method: 'PUT', body: { lastPage } }),
};

// ---------------------------------------------------------------- query keys

export const queryKeys = {
  me: ['me'] as const,
  categories: ['categories'] as const,
  books: (q: BookQuery) => ['books', q] as const,
  book: (id: string) => ['book', id] as const,
  history: (page: number, size: number) => ['member', 'history', page, size] as const,
  requests: (page: number) => ['member', 'requests', page] as const,
};

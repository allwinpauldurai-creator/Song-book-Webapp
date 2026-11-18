// Mirrors the backend DTOs (backend/src/main/java/com/example/songbook/**/dto). Dates are ISO strings.

export type Role = 'MEMBER' | 'PUBLISHER' | 'ADMIN';
export type MembershipStatus = 'ACTIVE' | 'SUSPENDED' | 'EXPIRED' | 'CANCELLED';

export interface FieldError {
  field: string;
  message: string;
}

export interface ApiErrorBody {
  timestamp?: string;
  status: number;
  code: string;
  message?: string;
  path?: string;
  fieldErrors?: FieldError[];
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface MembershipSummary {
  id: string;
  status: MembershipStatus;
  startDate: string;
  endDate?: string | null;
  valid: boolean;
}

export interface Me {
  id: string;
  fullName: string;
  /** E.164, e.g. +919876543210. Missing only on accounts created before sign-in moved to mobile numbers. */
  mobile?: string | null;
  mobileVerified: boolean;
  /** Optional contact address; not used to sign in. */
  email?: string | null;
  roles: Role[];
  membership?: MembershipSummary | null;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: Me;
}

export interface AccessTokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface MessageResponse {
  message: string;
}

export interface Category {
  id: string;
  name: string;
  description?: string | null;
}

export interface BookSummary {
  id: string;
  title: string;
  description?: string | null;
  category?: Category | null;
  coverUrl?: string | null;
}

export interface BookDetail extends BookSummary {
  effectiveDate: string;
  expiryDate?: string | null;
  pageCount?: number | null;
  updatedAt: string;
}

export interface AccessTicket {
  ticket: string;
  expiresAt: string;
  versionId: string;
  versionNumber: number;
  pageCount?: number | null;
  /** Relative API path including the ticket, e.g. /api/v1/books/{id}/content?ticket=… */
  contentUrl: string;
}

export interface ReadingProgress {
  bookId: string;
  lastPage: number;
  versionId: string;
  updatedAt: string;
}

export interface HistoryItem {
  bookId: string;
  title: string;
  coverUrl?: string | null;
  lastPage: number;
  updatedAt: string;
}

export type RequestType = 'CORRECTION' | 'ADDITION' | 'OTHER';
export type RequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface ModificationRequest {
  id: string;
  requestedBy: string;
  requesterName: string;
  bookId?: string | null;
  bookTitle?: string | null;
  requestType: RequestType;
  description: string;
  status: RequestStatus;
  reviewNote?: string | null;
  reviewedBy?: string | null;
  reviewedAt?: string | null;
  createdAt: string;
  version: number;
}

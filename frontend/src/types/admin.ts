// Admin DTOs, mirroring backend */dto and admin services. Dates are ISO strings.
import type { Category, MembershipStatus, Role } from './api';

export type AccountStatus = 'ACTIVE' | 'DISABLED' | 'LOCKED';
export type BookStatus = 'DRAFT' | 'PUBLISHED' | 'INACTIVE' | 'ARCHIVED';

export interface AdminStats {
  users: { total: number; active: number; disabled: number; locked: number; unverified: number; activeAdmins: number };
  membershipsValidToday: number;
  booksByStatus: Record<BookStatus, number>;
  pendingRequests: number;
  asOf: string;
}

export interface AdminUserSummary {
  id: string;
  fullName: string;
  mobile?: string | null;
  mobileVerified: boolean;
  email?: string | null;
  accountStatus: AccountStatus;
  roles: Role[];
  createdAt: string;
}

export interface AdminMembership {
  id: string;
  userId: string;
  userMobile?: string | null;
  userEmail?: string | null;
  userFullName: string;
  status: MembershipStatus;
  startDate: string;
  endDate?: string | null;
  validToday: boolean;
  notes?: string | null;
  createdBy?: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface AdminUserDetail extends AdminUserSummary {
  mobileVerifiedAt?: string | null;
  lockedUntil?: string | null;
  updatedAt: string;
  version: number;
  memberships: AdminMembership[];
}

export interface AdminCategory {
  id: string;
  name: string;
  description?: string | null;
  active: boolean;
  version: number;
}

export interface BookVersion {
  id: string;
  versionNumber: number;
  originalFilename: string;
  fileSize: number;
  pageCount?: number | null;
  checksumSha256: string;
  changeNote?: string | null;
  uploadedBy?: string | null;
  createdAt: string;
  current: boolean;
}

export interface AdminBookSummary {
  id: string;
  title: string;
  category?: Category | null;
  status: BookStatus;
  effectiveDate: string;
  expiryDate?: string | null;
  availableToday: boolean;
  coverUrl?: string | null;
  updatedAt: string;
}

export interface AdminBookDetail extends AdminBookSummary {
  description?: string | null;
  currentVersion?: BookVersion | null;
  uploadedBy?: string | null;
  createdAt: string;
  version: number;
}

export interface BookMetadata {
  title: string;
  description?: string | null;
  categoryId?: string | null;
  effectiveDate: string;
  expiryDate?: string | null;
  status: BookStatus;
}

export interface AuditLogEntry {
  id: string;
  actorUserId?: string | null;
  action: string;
  entityType?: string | null;
  entityId?: string | null;
  details?: Record<string, unknown> | null;
  ipAddress?: string | null;
  createdAt: string;
}

export const AUDIT_ACTIONS = [
  'USER_REGISTERED', 'EMAIL_VERIFIED', 'LOGIN_SUCCEEDED', 'LOGIN_FAILED', 'ACCOUNT_TEMPORARILY_LOCKED',
  'REFRESH_TOKEN_REUSE', 'PASSWORD_RESET_REQUESTED', 'PASSWORD_RESET', 'PASSWORD_CHANGED', 'PROFILE_UPDATED',
  'ADMIN_BOOTSTRAPPED', 'USER_CREATED', 'USER_UPDATED', 'MEMBERSHIP_CREATED', 'MEMBERSHIP_UPDATED',
  'CATEGORY_CREATED', 'CATEGORY_UPDATED', 'BOOK_CREATED', 'BOOK_UPDATED', 'BOOK_COVER_REPLACED',
  'BOOK_VERSION_UPLOADED', 'BOOK_ARCHIVED', 'PDF_READ_ACCESS', 'PDF_DOWNLOAD_ACCESS', 'MODIFICATION_REQUESTED',
  'MODIFICATION_REVIEWED',
] as const;

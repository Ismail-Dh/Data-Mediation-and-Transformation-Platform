export interface AuditLog {
  id: number;
  performedBy: string;
  performedByRole: string;
  action: string;
  targetEntity: string;
  targetId: string | null;
  details: string | null;
  httpStatus: number | null;
  errorMessage: string | null;
  errorCode: string | null;
  timestamp: string;
}

export interface AuditLogFilter {
  username?: string;
  role?: string;
  action?: string;
  httpStatus?: number;
}
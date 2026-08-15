export interface HourlyBucket {
  hour: string;
  count: number;
}

export interface ActionStat {
  action: string;
  total: number;
  errors: number;
}

export interface RoleStat {
  role: string;
  count: number;
}

export interface ErrorStat {
  code: string;
  count: number;
}

export interface MonitoringStatsResponse {
  // Global KPIs
  totalRequests: number;
  totalErrors: number;
  errorRatePct: number;
  avgDurationMs: number | null;

  // Pipelines (kept for backward compat with the admin dashboard widget —
  // NOT used on the /monitoring page anymore, which is platform-only)
  totalPipelines: number;
  configuredPipelines: number;

  // Payloads (idem — not used on /monitoring)
  totalPayloads: number;
  successfulPayloads: number;
  failedPayloads: number;

  // Time-series
  errorsByHour: HourlyBucket[];
  requestsByHour: HourlyBucket[];

  // Per-action breakdown
  requestsByAction: ActionStat[];

  // Real platform KPIs (audit_logs only, no pipeline/payload data)
  activeUsers24h: number;
  maxDurationMs: number | null;
  requestsByRole: RoleStat[];
  topErrors: ErrorStat[];
  lastActivityAt: string | null;
}
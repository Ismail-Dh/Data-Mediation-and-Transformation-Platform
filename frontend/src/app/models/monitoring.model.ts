export interface HourlyBucket {
  hour: string;
  count: number;
}

export interface ActionStat {
  action: string;
  total: number;
  errors: number;
}

export interface MonitoringStatsResponse {
  // Global KPIs
  totalRequests: number;
  totalErrors: number;
  errorRatePct: number;
  avgDurationMs: number | null;

  // Pipelines
  totalPipelines: number;
  configuredPipelines: number;

  // Payloads
  totalPayloads: number;
  successfulPayloads: number;
  failedPayloads: number;

  // Time-series
  errorsByHour: HourlyBucket[];
  requestsByHour: HourlyBucket[];

  // Per-action breakdown
  requestsByAction: ActionStat[];
}
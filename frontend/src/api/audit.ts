import { http } from './http';

export interface AuditLogEntry {
  id: number;
  eventId: string;
  userId?: string;
  actorName?: string;
  role?: string;
  service: string;
  resourceType: string;
  resourceId?: string;
  patientId?: string;
  businessId?: string;
  action: string;
  requestIp?: string;
  occurredAt: string;
  details: Record<string, unknown>;
}

export interface AuditLogQuery {
  service?: string;
  action?: string;
  resourceType?: string;
  resourceId?: string;
  userId?: string;
  patientId?: string;
  businessId?: string;
  from?: string;
  to?: string;
  limit?: number;
}

export async function getAuditLogs(params: AuditLogQuery) {
  return (await http.get<AuditLogEntry[]>('/audit/logs', { params })).data;
}

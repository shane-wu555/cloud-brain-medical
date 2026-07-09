import { http } from './http';

export interface ClinicalSuggestion {
  kind: 'diagnosis' | 'exam' | 'medication' | 'risk' | 'advice';
  label: string;
  content: string;
  source?: string;
  metadata?: Record<string, any>;
}

export interface ClinicalAssistanceResponse {
  aiRecordId: string;
  createdByType: 'AI';
  requiresHumanConfirmation: boolean;
  suggestions: ClinicalSuggestion[];
  provider: string;
  model: string;
  fallbackUsed: boolean;
}

export async function getClinicalAssistance(payload: {
  appointmentId: string;
  patientId: string;
  assistanceType?: 'initial' | 'post_report';
  chiefComplaint: string;
  presentIllness: string;
  pastHistory?: string;
  allergyHistory?: string;
  diagnosis?: string;
  prompt: string;
  historicalRecords?: Array<Record<string, any>>;
  reportResults?: Array<Record<string, any>>;
  availableExamItems?: Array<{ code: string; name: string; category: string }>;
  availableDrugs?: Array<{ drugName: string; specification?: string }>;
}) {
  return (await http.post<ClinicalAssistanceResponse>('/ai/clinical-assistance', payload)).data;
}

export interface ConsultationResponse {
  consultationId: string;
  aiRecordId: string;
  summary: string;
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH';
  recommendedDepartmentId: string;
  recommendedDepartmentName: string;
  recommendedDoctors: Array<{ doctorId: string; doctorName: string; reason: string }>;
  suggestOfflineUrgent: boolean;
  needsFollowUp: boolean;
  followUpQuestions: string[];
  recordDraft: string;
  provider: string;
  model: string;
  fallbackUsed: boolean;
}

export async function createReportDraft(payload: {
  orderId: string;
  reportType: string;
  itemName: string;
  findings?: string;
  conclusion?: string;
  context?: string;
}) {
  return (await http.post<{
    aiRecordId: string;
    findings: string;
    conclusion: string;
    advice: string;
    provider: string;
    model: string;
    fallbackUsed: boolean;
  }>('/ai/report-drafts', payload)).data;
}

export async function getPrescriptionSuggestions(payload: {
  appointmentId: string;
  patientId: string;
  diagnosis: string;
  chiefComplaint: string;
  allergyHistory?: string;
  medicationHistory?: string;
  prompt?: string;
}) {
  return (await http.post<{
    aiRecordId: string;
    suggestions: Array<{
      drugName: string;
      dosage: string;
      usage: string;
      frequency: string;
      days: number;
      note: string;
      source: string;
    }>;
    warnings: string[];
    provider: string;
    model: string;
    fallbackUsed: boolean;
  }>('/ai/prescription-suggestions', payload)).data;
}

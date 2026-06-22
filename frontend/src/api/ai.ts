import { http } from './http';

export interface ClinicalSuggestion {
  kind: 'diagnosis' | 'advice';
  label: string;
  content: string;
}

export interface ClinicalAssistanceResponse {
  aiRecordId: string;
  createdByType: 'AI';
  requiresHumanConfirmation: boolean;
  suggestions: ClinicalSuggestion[];
}

export async function getClinicalAssistance(payload: {
  appointmentId: string;
  patientId: string;
  chiefComplaint: string;
  presentIllness: string;
  prompt: string;
}) {
  return (await http.post<ClinicalAssistanceResponse>('/ai/clinical-assistance', payload)).data;
}

import { http } from './http';

export interface Drug {
  id: string;
  drugCode: string;
  drugName: string;
  specification: string;
  unit: string;
  unitPrice: number;
  quantity: number;
  warningThreshold: number;
}

export interface PrescriptionItem {
  id: string;
  drugId: string;
  drugName: string;
  quantity: number;
  dosage: string;
  usage: string;
  frequency: string;
  days: number;
  note?: string;
  unitPrice: number;
  amount: number;
}

export interface Prescription {
  id: string;
  prescriptionNo: string;
  appointmentId: string;
  medicalRecordId?: string;
  patientId: string;
  patientName?: string;
  doctorId: string;
  diagnosis: string;
  status: string;
  totalAmount: number;
  paymentOrderId?: string;
  aiAssistanceId?: string;
  aiAdoptionStatus?: string;
  aiRevisionNote?: string;
  dispensedBy?: string;
  returnedBy?: string;
  returnReason?: string;
  items: PrescriptionItem[];
}

export async function getDrugs(keyword?: string) {
  const { data } = await http.get<Drug[]>('/drugs', { params: { keyword } });
  return data;
}

export async function getPrescriptions(params: { patientId?: string; status?: string } = {}) {
  const { data } = await http.get<Prescription[]>('/prescriptions', { params });
  return data;
}

export async function createPrescription(payload: {
  appointmentId: string;
  medicalRecordId?: string;
  patientId: string;
  patientName?: string;
  diagnosis: string;
  aiAssistanceId?: string;
  aiAdoptionStatus?: 'AI_ACCEPTED' | 'AI_MODIFIED' | 'AI_REJECTED' | 'HUMAN_ONLY';
  aiRevisionNote?: string;
  items: Array<{
    drugId: string;
    quantity: number;
    dosage: string;
    usage: string;
    frequency: string;
    days: number;
    note?: string;
  }>;
}) {
  const { data } = await http.post<Prescription>('/prescriptions', payload);
  return data;
}

export async function dispensePrescription(id: string) {
  const { data } = await http.post<Prescription>(`/prescriptions/${id}/dispense`);
  return data;
}

export async function returnPrescription(id: string, reason: string) {
  const { data } = await http.post<Prescription>(`/prescriptions/${id}/return`, { reason });
  return data;
}

import { http } from './http';

export interface Drug {
  id: string;
  drugCode: string;
  drugName: string;
  specification: string;
  unit: string;
  unitPrice: number;
  dosageForm: string;
  storageCondition: string;
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
  createdAt?: string;
  confirmedAt?: string;
  paidAt?: string;
  dispensedAt?: string;
  returnedAt?: string;
  dispensedBy?: string;
  returnedBy?: string;
  returnReason?: string;
  items: PrescriptionItem[];
}

export type DrugReturnStatus = 'RETURNED' | 'RETURN_PENDING_REFUND' | 'RETURN_REFUNDED' | string;

export interface DrugReturnItem {
  id: string;
  returnId: string;
  prescriptionItemId: string;
  drugId: string;
  drugName: string;
  quantity: number;
  unitPrice: number;
  amount: number;
  batchNo?: string;
  batchNoMatched?: boolean;
  coldChainOrOpenedRejectType?: boolean;
  packageIntact?: boolean;
  sealBroken?: boolean;
  pharmacistNote?: string;
}

export interface DrugReturnOrder {
  id: string;
  returnNo: string;
  prescriptionId: string;
  prescriptionNo: string;
  patientId: string;
  patientName: string;
  doctorId: string;
  doctorOpinion: string;
  opinionTemplate?: string;
  status: DrugReturnStatus;
  totalAmount: number;
  pharmacistId?: string;
  pharmacistOpinion?: string;
  cashierId?: string;
  refundOrderId?: string;
  createdAt?: string;
  verifiedAt?: string;
  completedAt?: string;
  items: DrugReturnItem[];
}

export async function getDrugs(params: { keyword?: string; storageCondition?: string } = {}) {
  const { data } = await http.get<Drug[]>('/drugs', {
    params: {
      ...params,
      _searchTs: Date.now()
    }
  });
  return data;
}

export async function stockInDrug(id: string, payload: { quantity: number; reason?: string }) {
  const { data } = await http.post<Drug>(`/drugs/${id}/stock-in`, payload);
  return data;
}

export async function getPrescriptions(params: {
  patientId?: string;
  status?: string;
  view?: string;
  patientName?: string;
  prescriptionNo?: string;
  page?: number;
  size?: number;
} = {}) {
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

export async function createDrugReturn(prescriptionId: string, payload: {
  doctorOpinion: string;
  opinionTemplate?: string;
}) {
  const { data } = await http.post<DrugReturnOrder>(`/prescriptions/${prescriptionId}/drug-returns`, payload);
  return data;
}

export async function getDrugReturns(params: {
  patientId?: string;
  status?: string;
  patientName?: string;
  prescriptionNo?: string;
  returnNo?: string;
  page?: number;
  size?: number;
} = {}) {
  const { data } = await http.get<DrugReturnOrder[]>('/drug-returns', { params });
  return data;
}

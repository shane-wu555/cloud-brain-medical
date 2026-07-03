import { http } from './http';

export interface MedicalOrder {
  id: string;
  appointmentId: string;
  patientId: string;
  patientName: string;
  orderingDoctorId: string;
  orderType: 'CHECK' | 'LAB' | 'DISPOSAL';
  itemCode: string;
  itemName: string;
  purpose?: string;
  bodyPart?: string;
  amount: number;
  paymentStatus: string;
  status: string;
  roomId?: string;
  roomName?: string;
  roomLocation?: string;
  executingStaffId?: string;
  queueNumber?: number;
  urgency: string;
  triageSource?: string;
  triageReasons?: string;
  missedCount: number;
  resultSummary?: string;
  resultConfirmedBy?: string;
  resultConfirmedAt?: string;
  createdAt?: string;
  startedAt?: string;
  completedAt?: string;
}

export interface MedicalReport {
  id: string;
  orderId?: string;
  medicalOrderId?: string;
  reportType: string;
  status: string;
  findings: string;
  conclusion: string;
  advice: string;
  createdByType: string;
  aiTaskId?: string;
  modifiedFromAi: boolean;
  confirmedBy?: string;
  confirmedAt?: string;
}

export interface MedicalAttachment {
  id: string;
  medicalOrderId: string;
  objectKey: string;
  originalName: string;
  contentType?: string;
  sizeBytes: number;
  storageBucket: string;
  uploadedBy: string;
  createdAt: string;
}

export interface AiMedicalTask {
  id: string;
  medicalOrderId: string;
  externalTaskId: string;
  taskType: string;
  status: string;
  modelVersion?: string;
  rawOutput?: string | Record<string, unknown>;
  errorMessage?: string;
  updatedAt?: string;
}

export interface MedicalItem { code: string; name: string; category: 'CHECK' | 'LAB' | 'DISPOSAL' | 'DRUG'; price: number }
export interface LaboratoryResultItem {
  id?: string;
  itemCode: string;
  itemName: string;
  resultValue: string;
  unit?: string;
  referenceRange?: string;
  abnormalFlag?: string;
  createdByType?: string;
}

export interface Specimen {
  id: string;
  medicalOrderId: string;
  specimenType: string;
  barcode: string;
  status: string;
}

export async function getMedicalItems() { return (await http.get<MedicalItem[]>('/catalog/medical-items')).data }
export async function getMedicalOrders(params: Record<string, string | undefined> = {}) { return (await http.get<MedicalOrder[]>('/medical-orders', { params })).data }
export async function createMedicalOrder(payload: Record<string, unknown>) { return (await http.post<MedicalOrder>('/medical-orders', payload)).data }
export async function payMedicalOrder(id: string) { return (await http.post<MedicalOrder>(`/medical-orders/${id}/pay`)).data }
export async function callMedicalOrder(id: string) { return (await http.post<MedicalOrder>(`/medical-orders/${id}/call`)).data }
export async function startMedicalOrder(id: string) { return (await http.post<MedicalOrder>(`/medical-orders/${id}/start`)).data }
export async function missMedicalOrder(id: string) { return (await http.post<MedicalOrder>(`/medical-orders/${id}/miss`)).data }
export async function completeMedicalOrder(id: string, payload: { summary?: string; createdByType?: string; aiRecordId?: string }) { return (await http.post<MedicalOrder>(`/medical-orders/${id}/complete`, payload)).data }
export async function createReportDraft(id: string, payload: { findings: string; conclusion: string; advice: string }) { return (await http.post<MedicalReport>(`/medical-orders/${id}/reports/draft`, payload)).data }
export async function confirmReport(id: string, payload: { findings: string; conclusion: string; advice: string }) { return (await http.post<MedicalReport>(`/medical-orders/${id}/reports/confirm`, payload)).data }
export async function getReports() { return (await http.get<MedicalReport[]>('/medical-orders/reports')).data }
export async function uploadAttachment(id: string, file: File) { const data = new FormData(); data.append('file', file); return (await http.post<MedicalAttachment>(`/medical-orders/${id}/attachments`, data)).data }
export async function getAttachments(id: string) { return (await http.get<MedicalAttachment[]>(`/medical-orders/${id}/attachments`)).data }
export async function downloadAttachment(id: string, attachmentId: string) { return (await http.get<Blob>(`/medical-orders/${id}/attachments/${attachmentId}/content`, { responseType: 'blob' })).data }
export async function submitCt(id: string, attachmentId: string) { return (await http.post<AiMedicalTask>(`/medical-orders/${id}/ct-analysis`, { attachmentId })).data }
export async function refreshAiTask(taskId: string) { return (await http.get<AiMedicalTask>(`/medical-orders/ai-tasks/${taskId}`)).data }
export async function createSpecimen(orderId: string, specimenType: string, barcode: string) { return (await http.post<Specimen>(`/medical-orders/${orderId}/specimens`, { specimenType, barcode })).data }
export async function getSpecimens(orderId: string) { return (await http.get<Specimen[]>(`/medical-orders/${orderId}/specimens`)).data }
export async function transitionSpecimen(id: string, status: string, reason = '') { return (await http.post<Specimen>(`/medical-orders/specimens/${id}/status`, { status, reason })).data }
export async function saveLabResults(orderId: string, specimenId: string, items: Array<Record<string, unknown>>) { return (await http.post<LaboratoryResultItem[]>(`/medical-orders/${orderId}/laboratory-results`, { specimenId, items })).data }
export async function getLabResults(orderId: string) { return (await http.get<LaboratoryResultItem[]>(`/medical-orders/${orderId}/laboratory-results`)).data }

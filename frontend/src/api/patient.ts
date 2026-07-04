import { http } from './http';

export type IdType = 'ID_CARD' | 'PASSPORT' | 'HK_MACAO_TAIWAN' | 'OTHER';
export type Gender = 'MALE' | 'FEMALE' | 'UNKNOWN';

export interface PatientProfile {
  id?: string;
  userId?: string;
  accountId?: string | null;
  phone?: string;
  name: string;
  realNameVerified: boolean;
  idType?: IdType;
  idNumber?: string;
  gender?: Gender;
  birthDate?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface OfflinePatientPayload {
  idType: IdType;
  idNumber: string;
  name: string;
  phone?: string;
  gender: Gender;
  birthDate: string;
}

export function patientProfileId(patient: PatientProfile) {
  return patient.userId ?? patient.id ?? '';
}

export async function searchPatientByIdNumber(idNumber: string) {
  return (await http.get<PatientProfile[]>('/patients', { params: { idNumber } })).data;
}
export async function searchPatientByPhone(phone: string) {
  return (await http.get<PatientProfile[]>('/patients', { params: { phone } })).data;
}
export async function getPatientsByIds(patientIds: string[]) {
  if (!patientIds.length) return [];
  return (await http.get<PatientProfile[]>('/patients', { params: { ids: patientIds.join(',') } })).data;
}
export async function createOfflinePatient(payload: OfflinePatientPayload) {
  return (await http.post<PatientProfile>('/patients/offline', payload)).data;
}

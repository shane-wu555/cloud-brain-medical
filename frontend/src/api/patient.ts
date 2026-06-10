import { http } from './http';

export interface PatientProfile {
  id: string;
  name: string;
  gender: string;
  age: number;
  phone: string;
  tags: string[];
}

export async function getPatientProfile() {
  const { data } = await http.get<PatientProfile>('/patients/me');
  return data;
}


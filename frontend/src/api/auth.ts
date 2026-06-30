import { http } from './http';
import type { CurrentUser } from '../store/auth';

export interface LoginResponse {
  token: string;
  user: CurrentUser;
}

export async function login(username: string, password: string) {
  const { data } = await http.post<LoginResponse>('/auth/login', { username, password });
  return data;
}

export async function registerPatient(phone: string, password: string, name: string, smsCode = '000000') {
  const { data } = await http.post<LoginResponse>('/auth/register', { phone, password, name, smsCode });
  return data;
}

export interface StaffAccount {
  id: string;
  username: string;
  employeeNo: string;
  name: string;
  role: string;
  phone?: string | null;
  active: boolean;
  createdAt: string;
}

export interface StaffAccountPayload {
  employeeNo: string;
  name: string;
  role: string;
  phone?: string;
  password: string;
}

export async function getStaffAccounts(role?: string) {
  const { data } = await http.get<StaffAccount[]>('/auth/staff-accounts', { params: { role } });
  return data;
}

export async function createStaffAccount(payload: StaffAccountPayload) {
  const { data } = await http.post<StaffAccount>('/auth/staff-accounts', payload);
  return data;
}

export async function updateStaffAccount(
  id: string,
  payload: Pick<StaffAccountPayload, 'name' | 'role' | 'phone'>
) {
  const { data } = await http.put<StaffAccount>(`/auth/staff-accounts/${id}`, payload);
  return data;
}

export async function resetStaffAccountPassword(id: string, newPassword: string) {
  const { data } = await http.put<StaffAccount>(`/auth/staff-accounts/${id}/password`, { newPassword });
  return data;
}

export async function setStaffAccountActive(id: string, active: boolean) {
  const { data } = await http.put<StaffAccount>(`/auth/staff-accounts/${id}/active`, { active });
  return data;
}

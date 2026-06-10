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

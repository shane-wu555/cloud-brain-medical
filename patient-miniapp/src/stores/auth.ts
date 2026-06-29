import { defineStore } from 'pinia';
import { request } from '../api/http';

export interface PatientUser {
  id: string;
  username?: string;
  name: string;
  phone: string;
  role: 'PATIENT';
  realNameVerified?: boolean;
}

export interface PatientProfile {
  id: string;
  userId?: string;
  accountId?: string;
  phone?: string;
  name: string;
  idType: string;
  idNumber: string;
  gender: string;
  birthDate?: string;
}

interface PatientAccountState {
  patients: PatientProfile[];
  boundPatient?: PatientProfile | null;
  hasBoundPatient?: boolean;
}

interface AddPatientPayload {
  name: string;
  idType: string;
  idNumber: string;
  gender: string;
  birthDate: string;
}

function mergeUser(user: PatientUser): PatientUser {
  return {
    ...user,
    username: user.username || user.phone
  };
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: '',
    user: null as PatientUser | null,
    patients: [] as PatientProfile[],
    boundPatient: null as PatientProfile | null
  }),
  getters: {
    hasBoundPatient: (state) => !!state.boundPatient,
    currentPatientName: (state) => state.boundPatient?.name || ''
  },
  actions: {
    restore() {
      this.token = uni.getStorageSync('access_token') || '';
      this.user = uni.getStorageSync('current_user') || null;
      this.patients = uni.getStorageSync('patient_profiles') || [];
      this.boundPatient = uni.getStorageSync('bound_patient') || null;
      if (this.user) {
        this.user = mergeUser(this.user);
        uni.setStorageSync('current_user', this.user);
      }
    },
    async login(username: string, password: string) {
      const result = await request<{ token: string; user: PatientUser }>({
        url: '/auth/login',
        method: 'POST',
        data: { username, password }
      });
      if (result.user.role !== 'PATIENT') {
        throw new Error('请使用患者账号登录');
      }
      this.token = result.token;
      this.user = mergeUser(result.user);
      uni.setStorageSync('access_token', result.token);
      uni.setStorageSync('current_user', this.user);
      await this.loadProfile();
    },
    async register(phone: string, password: string, name: string, smsCode: string) {
      const result = await request<{ token: string; user: PatientUser }>({
        url: '/auth/register',
        method: 'POST',
        data: { phone, password, name, smsCode }
      });
      this.token = result.token;
      this.user = mergeUser(result.user);
      uni.setStorageSync('access_token', result.token);
      uni.setStorageSync('current_user', this.user);
      await this.loadProfile();
    },
    async sendCode(phone: string, purpose: 'REGISTER' | 'LOGIN' | 'RESET_PASSWORD') {
      return request<{ expiresIn: number; devCode?: string }>({
        url: '/auth/sms-codes',
        method: 'POST',
        data: { phone, purpose }
      });
    },
    async smsLogin(phone: string, smsCode: string) {
      const result = await request<{ token: string; user: PatientUser }>({
        url: '/auth/sms-login',
        method: 'POST',
        data: { phone, smsCode }
      });
      this.token = result.token;
      this.user = mergeUser(result.user);
      uni.setStorageSync('access_token', result.token);
      uni.setStorageSync('current_user', this.user);
      await this.loadProfile();
    },
    async resetPassword(phone: string, smsCode: string, newPassword: string) {
      await request<void>({
        url: '/auth/reset-password',
        method: 'POST',
        data: { phone, smsCode, newPassword }
      });
    },
    async loadProfile() {
      const state = await request<PatientAccountState>({ url: '/patients/me', method: 'GET' });
      this.patients = state.patients || [];
      this.boundPatient = state.boundPatient || null;
      uni.setStorageSync('patient_profiles', this.patients);
      uni.setStorageSync('bound_patient', this.boundPatient);
      return state;
    },
    async addPatient(payload: AddPatientPayload) {
      const profile = await request<PatientProfile>({
        url: '/patients/me/profiles',
        method: 'POST',
        data: payload
      });
      await this.loadProfile();
      return profile;
    },
    async bindPatient(patientId: string) {
      const profile = await request<PatientProfile>({
        url: '/patients/me/bound-patient',
        method: 'PUT',
        data: { patientId }
      });
      this.boundPatient = profile;
      await this.loadProfile();
      return profile;
    },
    requireBoundPatient() {
      if (!this.boundPatient) {
        throw new Error('请先添加并绑定就诊人');
      }
      return this.boundPatient;
    }
  }
});

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
  medicalInsuranceBound?: boolean;
  medicalInsuranceNo?: string;
}

interface PatientAccountState {
  patients?: PatientProfile[];
  profiles?: PatientProfile[];
  boundPatient?: PatientProfile | null;
  bound?: PatientProfile | null;
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

function patientStorageKey(accountId?: string) {
  return `bound_patient_${accountId || 'anonymous'}`;
}

function normalizePatient(patient: PatientProfile): PatientProfile {
  return {
    ...patient,
    medicalInsuranceBound: Boolean(patient.medicalInsuranceBound),
    medicalInsuranceNo: patient.medicalInsuranceNo || undefined
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
    clearPatientState(previousAccountId?: string) {
      this.patients = [];
      this.boundPatient = null;
      uni.removeStorageSync('patient_profiles');
      uni.removeStorageSync('bound_patient');
      if (previousAccountId) {
        uni.removeStorageSync(patientStorageKey(previousAccountId));
      }
    },
    restore() {
      this.token = uni.getStorageSync('access_token') || '';
      this.user = uni.getStorageSync('current_user') || null;
      this.patients = (uni.getStorageSync('patient_profiles') || []).map(normalizePatient);
      this.boundPatient = uni.getStorageSync('bound_patient') ? normalizePatient(uni.getStorageSync('bound_patient')) : null;
      if (this.user) {
        this.user = mergeUser(this.user);
        uni.setStorageSync('current_user', this.user);
      }
    },
    logout() {
      const accountId = this.user?.id;
      this.token = '';
      this.user = null;
      this.patients = [];
      this.boundPatient = null;
      uni.removeStorageSync('access_token');
      uni.removeStorageSync('current_user');
      uni.removeStorageSync('patient_profiles');
      uni.removeStorageSync('bound_patient');
      if (accountId) {
        uni.removeStorageSync(patientStorageKey(accountId));
      }
    },
    async login(username: string, password: string) {
      const previousAccountId = this.user?.id;
      const result = await request<{ token: string; user: PatientUser }>({
        url: '/auth/login',
        method: 'POST',
        data: { username, password }
      });
      if (result.user.role !== 'PATIENT') {
        throw new Error('请使用患者账号登录');
      }
      this.clearPatientState(previousAccountId);
      this.token = result.token;
      this.user = mergeUser(result.user);
      uni.setStorageSync('access_token', result.token);
      uni.setStorageSync('current_user', this.user);
    },
    async register(phone: string, password: string, name: string, smsCode: string) {
      const previousAccountId = this.user?.id;
      const result = await request<{ token: string; user: PatientUser }>({
        url: '/auth/register',
        method: 'POST',
        data: { phone, password, name, smsCode }
      });
      this.clearPatientState(previousAccountId);
      this.token = result.token;
      this.user = mergeUser(result.user);
      uni.setStorageSync('access_token', result.token);
      uni.setStorageSync('current_user', this.user);
    },
    async sendCode(phone: string, purpose: 'REGISTER' | 'LOGIN' | 'RESET_PASSWORD') {
      return request<{ expiresIn: number; devCode?: string }>({
        url: '/auth/sms-codes',
        method: 'POST',
        data: { phone, purpose }
      });
    },
    async smsLogin(phone: string, smsCode: string) {
      const previousAccountId = this.user?.id;
      const result = await request<{ token: string; user: PatientUser }>({
        url: '/auth/sms-login',
        method: 'POST',
        data: { phone, smsCode }
      });
      this.clearPatientState(previousAccountId);
      this.token = result.token;
      this.user = mergeUser(result.user);
      uni.setStorageSync('access_token', result.token);
      uni.setStorageSync('current_user', this.user);
    },
    async resetPassword(phone: string, smsCode: string, newPassword: string) {
      await request<void>({
        url: '/auth/reset-password',
        method: 'POST',
        data: { phone, smsCode, newPassword }
      });
    },
    async changePassword(oldPassword: string, newPassword: string) {
      await request<void>({
        url: '/auth/change-password',
        method: 'POST',
        data: { oldPassword, newPassword }
      });
    },
    async loadProfile() {
      const state = await request<PatientAccountState>({ url: '/patients/me', method: 'GET' });
      this.patients = (state.patients || state.profiles || []).map(normalizePatient);
      const scopedBoundPatient = this.user?.id ? uni.getStorageSync(patientStorageKey(this.user.id)) : null;
      const storedBoundPatient = scopedBoundPatient || uni.getStorageSync('bound_patient') || null;
      const normalizedStoredBoundPatient = storedBoundPatient ? normalizePatient(storedBoundPatient) : null;
      const serverBoundPatient = state.boundPatient || state.bound || null;
      const normalizedServerBoundPatient = serverBoundPatient ? normalizePatient(serverBoundPatient) : null;
      const preferredBoundPatient = storedBoundPatient && this.patients.some((patient) => patient.id === storedBoundPatient.id)
        ? this.patients.find((patient) => patient.id === normalizedStoredBoundPatient?.id) || normalizedStoredBoundPatient
        : normalizedServerBoundPatient;
      this.boundPatient = preferredBoundPatient || null;
      uni.setStorageSync('patient_profiles', this.patients);
      uni.setStorageSync('bound_patient', this.boundPatient);
      if (this.user?.id && this.boundPatient) {
        uni.setStorageSync(patientStorageKey(this.user.id), this.boundPatient);
      }
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
      const localProfile = this.patients.find((patient) => patient.id === patientId) || normalizePatient(profile);
      this.boundPatient = {
        ...localProfile,
        medicalInsuranceBound: Boolean(localProfile.medicalInsuranceBound),
        medicalInsuranceNo: localProfile.medicalInsuranceNo
      };
      uni.setStorageSync('bound_patient', this.boundPatient);
      if (this.user?.id) {
        uni.setStorageSync(patientStorageKey(this.user.id), this.boundPatient);
      }
      await this.loadProfile();
      return this.boundPatient;
    },
    async bindMedicalInsurance(patientId: string) {
      const patient = this.patients.find((item) => item.id === patientId);
      if (!patient) {
        throw new Error('请先选择就诊人');
      }
      await request<PatientProfile>({
        url: `/patients/me/profiles/${encodeURIComponent(patientId)}/medical-insurance`,
        method: 'PUT'
      });
      await this.loadProfile();
      return this.patients.find((item) => item.id === patientId) || this.boundPatient;
    },
    isMedicalInsuranceBound(patientId?: string) {
      if (!patientId) {
        return false;
      }
      const patient = this.patients.find((item) => item.id === patientId);
      if (patient) {
        return Boolean(patient.medicalInsuranceBound);
      }
      return this.boundPatient?.id === patientId ? Boolean(this.boundPatient.medicalInsuranceBound) : false;
    },
    requireBoundPatient() {
      if (!this.boundPatient) {
        throw new Error('请先添加并绑定就诊人');
      }
      return this.boundPatient;
    }
  }
});

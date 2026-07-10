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

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: '',
    user: null as PatientUser | null,
    patients: [] as PatientProfile[],
    boundPatient: null as PatientProfile | null,
    insuranceBindings: {} as Record<string, string>
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
      this.insuranceBindings = uni.getStorageSync('medical_insurance_bindings') || {};
      if (this.user) {
        this.user = mergeUser(this.user);
        uni.setStorageSync('current_user', this.user);
      }
      this.applyInsuranceBindings();
    },
    logout() {
      const accountId = this.user?.id;
      this.token = '';
      this.user = null;
      this.patients = [];
      this.boundPatient = null;
      this.insuranceBindings = {};
      uni.removeStorageSync('access_token');
      uni.removeStorageSync('current_user');
      uni.removeStorageSync('patient_profiles');
      uni.removeStorageSync('bound_patient');
      uni.removeStorageSync('medical_insurance_bindings');
      if (accountId) {
        uni.removeStorageSync(patientStorageKey(accountId));
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
      this.patients = state.patients || state.profiles || [];
      const scopedBoundPatient = this.user?.id ? uni.getStorageSync(patientStorageKey(this.user.id)) : null;
      const storedBoundPatient = scopedBoundPatient || uni.getStorageSync('bound_patient') || null;
      const serverBoundPatient = state.boundPatient || state.bound || null;
      const preferredBoundPatient = storedBoundPatient && this.patients.some((patient) => patient.id === storedBoundPatient.id)
        ? storedBoundPatient
        : serverBoundPatient;
      this.boundPatient = preferredBoundPatient;
      this.applyInsuranceBindings();
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
      const localProfile = this.patients.find((patient) => patient.id === patientId) || profile;
      this.boundPatient = {
        ...localProfile,
        medicalInsuranceBound: Boolean(this.insuranceBindings[patientId]),
        medicalInsuranceNo: this.insuranceBindings[patientId]
      };
      uni.setStorageSync('bound_patient', this.boundPatient);
      if (this.user?.id) {
        uni.setStorageSync(patientStorageKey(this.user.id), this.boundPatient);
      }
      await this.loadProfile();
      return this.boundPatient;
    },
    bindMedicalInsurance(patientId: string) {
      const patient = this.patients.find((item) => item.id === patientId);
      if (!patient) {
        throw new Error('请先选择就诊人');
      }
      const tail = patient.idNumber ? patient.idNumber.slice(-6).replace(/\D/g, '') : patient.id.slice(-6);
      this.insuranceBindings = {
        ...this.insuranceBindings,
        [patientId]: `医保电子凭证 ${tail || '已认证'}`
      };
      uni.setStorageSync('medical_insurance_bindings', this.insuranceBindings);
      this.applyInsuranceBindings();
      return this.boundPatient;
    },
    isMedicalInsuranceBound(patientId?: string) {
      return Boolean(patientId && this.insuranceBindings[patientId]);
    },
    applyInsuranceBindings() {
      this.patients = this.patients.map((patient) => ({
        ...patient,
        medicalInsuranceBound: Boolean(this.insuranceBindings[patient.id]),
        medicalInsuranceNo: this.insuranceBindings[patient.id]
      }));
      if (this.boundPatient) {
        const bound = this.patients.find((patient) => patient.id === this.boundPatient?.id);
        this.boundPatient = bound || {
          ...this.boundPatient,
          medicalInsuranceBound: Boolean(this.insuranceBindings[this.boundPatient.id]),
          medicalInsuranceNo: this.insuranceBindings[this.boundPatient.id]
        };
      }
    },
    requireBoundPatient() {
      if (!this.boundPatient) {
        throw new Error('请先添加并绑定就诊人');
      }
      return this.boundPatient;
    }
  }
});

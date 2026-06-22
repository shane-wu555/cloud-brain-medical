import { defineStore } from 'pinia';
import { request } from '../api/http';

interface PatientUser {
  id: string;
  name: string;
  phone: string;
  role: 'PATIENT';
  realNameVerified: boolean;
}

interface PatientProfile extends PatientUser { idCard?: string; gender?: string; birthDate?: string }

export const useAuthStore = defineStore('auth', {
  state: () => ({ token: '', user: null as PatientUser | null }),
  actions: {
    restore() {
      this.token = uni.getStorageSync('access_token') || '';
      this.user = uni.getStorageSync('current_user') || null;
    },
    async login(username: string, password: string) {
      const result = await request<{ token: string; user: PatientUser }>({
        url: '/auth/login', method: 'POST', data: { username, password }
      });
      if (result.user.role !== 'PATIENT') throw new Error('请使用患者账号登录');
      this.token = result.token;
      this.user = result.user;
      uni.setStorageSync('access_token', result.token);
      uni.setStorageSync('current_user', result.user);
    },
    async register(phone: string, password: string, name: string, smsCode: string) {
      const result = await request<{ token: string; user: PatientUser }>({
        url: '/auth/register', method: 'POST', data: { phone, password, name, smsCode }
      });
      this.token = result.token;
      this.user = result.user;
      uni.setStorageSync('access_token', result.token);
      uni.setStorageSync('current_user', result.user);
    },
    async sendCode(phone: string, purpose: 'REGISTER' | 'LOGIN' | 'RESET_PASSWORD') {
      return request<{ expiresIn: number; devCode?: string }>({
        url: '/auth/sms-codes', method: 'POST', data: { phone, purpose }
      });
    },
    async smsLogin(phone: string, smsCode: string) {
      const result = await request<{ token: string; user: PatientUser }>({
        url: '/auth/sms-login', method: 'POST', data: { phone, smsCode }
      });
      this.token = result.token; this.user = result.user;
      uni.setStorageSync('access_token', result.token); uni.setStorageSync('current_user', result.user);
    },
    async resetPassword(phone: string, smsCode: string, newPassword: string) {
      await request<void>({ url: '/auth/reset-password', method: 'POST', data: { phone, smsCode, newPassword } });
    },
    async loadProfile() {
      const profile = await request<PatientProfile>({ url: '/patients/me', method: 'GET' });
      if (this.user) {
        this.user.name = profile.name; this.user.phone = profile.phone;
        this.user.realNameVerified = profile.realNameVerified;
        uni.setStorageSync('current_user', this.user);
      }
      return profile;
    },
    async verifyRealName(name: string, idCard: string) {
      const profile = await request<PatientProfile>({
        url: '/patients/me/real-name', method: 'PUT', data: { name, idCard }
      });
      if (this.user) {
        this.user.name = profile.name; this.user.realNameVerified = true;
        uni.setStorageSync('current_user', this.user);
      }
      return profile;
    }
  }
});

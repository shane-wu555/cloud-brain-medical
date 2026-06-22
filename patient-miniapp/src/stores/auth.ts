import { defineStore } from 'pinia';
import { request } from '../api/http';

interface PatientUser {
  id: string;
  name: string;
  phone: string;
  role: 'PATIENT';
  realNameVerified: boolean;
}

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
    }
  }
});

import { defineStore } from 'pinia';
import { login } from '../api/auth';

export type Role =
  | 'CASHIER'
  | 'OUTPATIENT_DOCTOR'
  | 'CHECK_DOCTOR'
  | 'LAB_DOCTOR'
  | 'DISPOSAL_DOCTOR'
  | 'PHARMACY_DOCTOR'
  | 'ADMIN';

export interface CurrentUser {
  id: string;
  name: string;
  phone?: string;
  role: Role;
  realNameVerified?: boolean;
  permissions: string[];
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('access_token') ?? '',
    user: JSON.parse(localStorage.getItem('current_user') ?? 'null') as CurrentUser | null
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token && state.user),
    homePath: (state) => {
      if (state.user?.role === 'OUTPATIENT_DOCTOR') return '/doctor/outpatient';
      if (state.user?.role === 'CHECK_DOCTOR') return '/doctor/check';
      if (state.user?.role === 'LAB_DOCTOR') return '/doctor/lab';
      if (state.user?.role === 'DISPOSAL_DOCTOR') return '/doctor/disposal';
      if (state.user?.role === 'PHARMACY_DOCTOR') return '/doctor/pharmacy';
      if (state.user?.role === 'CASHIER') return '/cashier';
      if (state.user?.role === 'ADMIN') return '/admin';
      return '/login';
    }
  },
  actions: {
    async signIn(username: string, password: string) {
      const result = await login(username, password);
      this.token = result.token;
      this.user = result.user;
      localStorage.setItem('access_token', result.token);
      localStorage.setItem('current_user', JSON.stringify(result.user));
      return this.homePath;
    },
    signOut() {
      this.token = '';
      this.user = null;
      localStorage.removeItem('access_token');
      localStorage.removeItem('current_user');
    }
  }
});

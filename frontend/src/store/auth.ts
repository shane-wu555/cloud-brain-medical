import { defineStore } from 'pinia';
import { login } from '../api/auth';

export type Role = 'PATIENT' | 'DOCTOR' | 'ADMIN';

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
      if (state.user?.role === 'DOCTOR') return '/doctor';
      if (state.user?.role === 'ADMIN') return '/admin';
      return '/patient';
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

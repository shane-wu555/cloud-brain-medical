import axios from 'axios';

export const http = axios.create({
  baseURL: '/api',
  timeout: 60000
});

const AUTH_FREE_PATHS = new Set([
  '/auth/login',
  '/auth/register',
  '/auth/sms-codes',
  '/auth/sms-login',
  '/auth/reset-password'
]);

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('access_token');
  const requestPath = config.url ?? '';
  if (token && !AUTH_FREE_PATHS.has(requestPath)) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});


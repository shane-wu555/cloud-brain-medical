import { defineStore } from 'pinia';
import { request } from '../api/http';

interface PatientUser {
  id: string;
  username?: string;
  name: string;
  phone: string;
  role: 'PATIENT';
  realNameVerified: boolean;
  idCard?: string;
}

interface PatientProfile extends PatientUser {
  gender?: string;
  birthDate?: string;
}

interface LocalRealNameState {
  name: string;
  idCard?: string;
  realNameVerified: true;
}

const REAL_NAME_STORAGE_KEY = 'patient_real_name_state';

function readRealNameStateMap(): Record<string, LocalRealNameState> {
  return uni.getStorageSync(REAL_NAME_STORAGE_KEY) || {};
}

function writeRealNameStateMap(stateMap: Record<string, LocalRealNameState>) {
  uni.setStorageSync(REAL_NAME_STORAGE_KEY, stateMap);
}

function getUserStorageKeys(user: Pick<PatientUser, 'id' | 'username' | 'phone'>): string[] {
  return [user.id, user.username, user.phone].filter((value): value is string => !!value);
}

function findLocalRealNameState(user: Pick<PatientUser, 'id' | 'username' | 'phone'>): LocalRealNameState | null {
  const stateMap = readRealNameStateMap();
  for (const key of getUserStorageKeys(user)) {
    if (stateMap[key]) {
      return stateMap[key];
    }
  }
  return null;
}

function saveLocalRealNameState(user: Pick<PatientUser, 'id' | 'username' | 'phone'>, state: LocalRealNameState) {
  const stateMap = readRealNameStateMap();
  for (const key of getUserStorageKeys(user)) {
    stateMap[key] = state;
  }
  writeRealNameStateMap(stateMap);
}

function mergeUser(user: PatientUser, cachedUser?: PatientUser | null): PatientUser {
  const merged: PatientUser = {
    ...user,
    username: user.username || user.phone,
    idCard: user.idCard || cachedUser?.idCard
  };

  const localRealNameState = findLocalRealNameState(merged);
  if (localRealNameState?.realNameVerified) {
    merged.name = localRealNameState.name;
    merged.realNameVerified = true;
    merged.idCard = localRealNameState.idCard || merged.idCard;
  }

  return merged;
}

export const useAuthStore = defineStore('auth', {
  state: () => ({ token: '', user: null as PatientUser | null }),
  actions: {
    restore() {
      this.token = uni.getStorageSync('access_token') || '';
      this.user = uni.getStorageSync('current_user') || null;
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
      const cachedUser = uni.getStorageSync('current_user') as PatientUser | null;
      this.token = result.token;
      this.user = mergeUser(result.user, cachedUser);
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
      const cachedUser = uni.getStorageSync('current_user') as PatientUser | null;
      this.token = result.token;
      this.user = mergeUser(result.user, cachedUser);
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
    async loadProfile() {
      const profile = await request<PatientProfile>({ url: '/patients/me', method: 'GET' });
      if (this.user) {
        this.user = mergeUser(
          {
            ...this.user,
            phone: profile.phone,
            name: this.user.realNameVerified ? this.user.name : profile.name,
            realNameVerified: profile.realNameVerified,
            idCard: profile.idCard || this.user.idCard
          },
          this.user
        );
        uni.setStorageSync('current_user', this.user);
      }
      return profile;
    },
    async verifyRealName(name: string, idCard: string) {
      const realName = name.trim();
      if (!realName) {
        throw new Error('请输入真实姓名');
      }
      if (!this.user) {
        throw new Error('登录状态已失效，请重新登录');
      }

      this.user = {
        ...this.user,
        name: realName,
        username: this.user.username || this.user.phone,
        realNameVerified: true,
        idCard: idCard.trim() || this.user.idCard
      };
      saveLocalRealNameState(this.user, {
        name: this.user.name,
        idCard: this.user.idCard,
        realNameVerified: true
      });
      uni.setStorageSync('current_user', this.user);
      return { ...this.user };
    }
  }
});

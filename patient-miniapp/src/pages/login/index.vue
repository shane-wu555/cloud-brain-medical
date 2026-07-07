<template>
  <patient-nav-bar title="患者登录" />
  <view class="page login-page">
    <view class="brand-hero">
      <view class="brand-badge">Cloud Brain Medical</view>
      <view class="brand-title">智慧云脑诊疗</view>
    </view>

    <view class="login-card">
      <view v-if="isLoginMode" class="tabs">
        <text
          v-for="item in loginTabs"
          :key="item.value"
          :class="{ active: mode === item.value }"
          @tap="switchMode(item.value)"
        >
          {{ item.label }}
        </text>
      </view>

      <view v-else class="mode-header">
        <text class="mode-title">{{ formTitle }}</text>
        <text class="mode-back" @tap="switchMode('PASSWORD')">返回登录</text>
      </view>

      <view v-if="isLoginMode" class="form-title">
        <text>{{ formTitle }}</text>
      </view>

      <view class="phone-row">
        <text class="phone-prefix">+86</text>
        <input v-model="phone" class="input phone-input" type="number" placeholder="请输入手机号" />
      </view>

      <input
        v-if="mode === 'PASSWORD' || mode === 'REGISTER' || mode === 'RESET'"
        v-model="password"
        class="input"
        password
        :placeholder="passwordPlaceholder"
      />

      <input
        v-if="mode === 'REGISTER'"
        v-model="name"
        class="input"
        placeholder="请输入就诊人姓名"
      />

      <view v-if="mode !== 'PASSWORD'" class="code-row">
        <input
          v-model="smsCode"
          class="input code-input"
          type="number"
          placeholder="请输入短信验证码"
        />
        <text class="code-button" @tap="sendCode()">获取验证码</text>
      </view>

      <view class="actions">
        <text
          v-for="item in actionTabs"
          :key="item.value"
          class="action-link primary-action"
          @tap="handleAction(item.value)"
        >
          {{ item.label }}
        </text>
      </view>

      <view v-if="isLoginMode" class="assist-row">
        <text @tap="switchMode('REGISTER')">新用户注册</text>
        <text class="assist-divider"></text>
        <text @tap="switchMode('RESET')">忘记密码</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useAuthStore } from '../../stores/auth';

type Mode = 'PASSWORD' | 'SMS' | 'REGISTER' | 'RESET';
type ActionType = 'SUBMIT';
type SmsPurpose = 'REGISTER' | 'LOGIN' | 'RESET_PASSWORD';

const auth = useAuthStore();
const mode = ref<Mode>('PASSWORD');
const phone = ref('');
const password = ref('');
const name = ref('');
const smsCode = ref('');

const loginTabs = [
  { value: 'PASSWORD' as Mode, label: '密码登录' },
  { value: 'SMS' as Mode, label: '验证码登录' }
];

const isLoginMode = computed(() => mode.value === 'PASSWORD' || mode.value === 'SMS');

const submitLabel = computed(
  () =>
    ({
      PASSWORD: '登录',
      SMS: '登录',
      REGISTER: '完成注册',
      RESET: '重置密码'
    })[mode.value]
);

const formTitle = computed(
  () =>
    ({
      PASSWORD: '密码登录',
      SMS: '验证码登录',
      REGISTER: '注册患者账号',
      RESET: '找回登录密码'
    })[mode.value]
);

const passwordPlaceholder = computed(() => (mode.value === 'RESET' ? '请输入新密码' : '请输入密码'));

const actionTabs = computed((): Array<{ value: ActionType; label: string }> => [
  { value: 'SUBMIT', label: submitLabel.value }
]);

function switchMode(nextMode: Mode) {
  mode.value = nextMode;
}

function handleAction(action: ActionType) {
  if (action === 'SUBMIT') {
    void submit();
  }
}

function normalizePhone(rawPhone: string) {
  const digits = rawPhone.replace(/[^\d]/g, '');
  if (digits.length === 13 && digits.startsWith('86')) {
    return digits.slice(2);
  }
  return digits;
}

function currentPhone() {
  const normalized = normalizePhone(phone.value);
  phone.value = normalized;
  return normalized;
}

function navigateToHome(successMessage: string) {
  uni.showToast({ title: successMessage, icon: 'success' });
  setTimeout(() => {
    uni.reLaunch({ url: '/pages/home/index' });
  }, 200);
}

async function sendCode() {
  const normalizedPhone = currentPhone();

  try {
    const purpose: SmsPurpose =
      mode.value === 'REGISTER'
        ? 'REGISTER'
        : mode.value === 'RESET'
          ? 'RESET_PASSWORD'
          : 'LOGIN';
    await auth.sendCode(normalizedPhone, purpose);
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' });
  }
}

async function submit() {
  const normalizedPhone = currentPhone();

  try {
    if (mode.value === 'PASSWORD') {
      await auth.login(normalizedPhone, password.value);
      navigateToHome('密码登录成功');
      return;
    }

    if (mode.value === 'SMS') {
      await auth.smsLogin(normalizedPhone, smsCode.value);
      navigateToHome('验证码登录成功');
      return;
    }

    if (mode.value === 'REGISTER') {
      await auth.register(normalizedPhone, password.value, name.value, smsCode.value);
      navigateToHome('注册成功');
      return;
    }

    await auth.resetPassword(normalizedPhone, smsCode.value, password.value);
    uni.showToast({ title: '密码已重置', icon: 'success' });
    mode.value = 'PASSWORD';
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' });
  }
}
</script>

<style scoped>
.login-page {
  position: relative;
  overflow: hidden;
  padding: 40rpx 28rpx 56rpx;
  background:
    radial-gradient(circle at 14% 8%, rgba(12, 189, 204, 0.18), transparent 34%),
    radial-gradient(circle at 86% 0%, rgba(8, 153, 165, 0.16), transparent 30%),
    linear-gradient(180deg, #eafafa 0%, #f7fbfd 42%, #eef8f8 100%);
}

.login-page::before {
  position: absolute;
  top: 120rpx;
  right: -86rpx;
  width: 280rpx;
  height: 280rpx;
  border: 28rpx solid rgba(12, 189, 204, 0.08);
  border-radius: 50%;
  content: "";
}

.brand-hero {
  position: relative;
  z-index: 1;
  padding: 34rpx 8rpx 30rpx;
  color: #0d3d5c;
}

.brand-badge {
  display: inline-flex;
  padding: 10rpx 18rpx;
  border: 1px solid rgba(12, 189, 204, 0.24);
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.72);
  color: #0899a5;
  font-size: 23rpx;
  font-weight: 700;
  letter-spacing: 0;
  box-shadow: 0 10rpx 28rpx rgba(8, 153, 165, 0.08);
}

.brand-title {
  margin-top: 22rpx;
  color: #0d3d5c;
  font-size: 48rpx;
  font-weight: 700;
  line-height: 1.16;
}

.login-card {
  position: relative;
  z-index: 1;
  padding: 34rpx 30rpx 32rpx;
  border: 1px solid rgba(255, 255, 255, 0.76);
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.94);
  box-shadow:
    0 24rpx 62rpx rgba(8, 153, 165, 0.15),
    0 8rpx 24rpx rgba(10, 60, 100, 0.08);
}

.tabs {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12rpx;
  margin: 0 0 30rpx;
  padding: 10rpx;
  border: 1px solid #d9f3f5;
  border-radius: 18rpx;
  background: #ecfbfc;
}

.tabs text {
  padding: 18rpx 8rpx;
  border-radius: 12rpx;
  color: #64748b;
  font-size: 29rpx;
  font-weight: 700;
  text-align: center;
}

.tabs .active {
  background: #ffffff;
  color: #0899a5;
  box-shadow: 0 8rpx 22rpx rgba(8, 153, 165, 0.15);
}

.mode-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  margin-bottom: 26rpx;
}

.mode-title {
  color: #0d3d5c;
  font-size: 36rpx;
  font-weight: 700;
  line-height: 1.3;
}

.mode-back {
  flex-shrink: 0;
  padding: 10rpx 16rpx;
  border-radius: 999rpx;
  background: #e6f9fa;
  color: #0899a5;
  font-size: 25rpx;
  font-weight: 700;
}

.form-title {
  display: flex;
  align-items: center;
  margin: 4rpx 0 20rpx;
  color: #0d3d5c;
  font-size: 32rpx;
  font-weight: 700;
}

.form-title::before {
  width: 8rpx;
  height: 32rpx;
  margin-right: 12rpx;
  border-radius: 999rpx;
  background: linear-gradient(180deg, #0cbdcc 0%, #0899a5 100%);
  content: "";
}

.phone-row,
.code-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.code-row {
  margin-top: 18rpx;
}

.login-card .input {
  height: 88rpx;
  margin-top: 18rpx;
  padding-top: 0;
  padding-bottom: 0;
  line-height: 88rpx;
  box-sizing: border-box;
}

.phone-row .input,
.code-row .input {
  margin-top: 0;
}

.phone-prefix {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 88rpx;
  height: 88rpx;
  padding: 0 18rpx;
  border: 1px solid #ccecef;
  border-radius: 14rpx;
  background: #f7fdfd;
  color: #0d3d5c;
  font-size: 30rpx;
  font-weight: 700;
  text-align: center;
  box-sizing: border-box;
}

.phone-input,
.code-input {
  flex: 1;
  min-width: 0;
}

.code-button {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 184rpx;
  height: 88rpx;
  border: 1px solid #a8e8ec;
  border-radius: 14rpx;
  background: #e6f9fa;
  color: #0899a5;
  font-size: 27rpx;
  font-weight: 700;
  box-sizing: border-box;
}

.actions {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  margin-top: 32rpx;
}

.action-link {
  display: block;
  padding: 24rpx 20rpx;
  border-radius: 16rpx;
  text-align: center;
  font-size: 32rpx;
  font-weight: 700;
  line-height: 1.25;
  box-sizing: border-box;
}

.primary-action {
  background: linear-gradient(135deg, #0cbdcc 0%, #0899a5 100%);
  color: #ffffff;
  box-shadow: 0 14rpx 30rpx rgba(12, 189, 204, 0.28);
}


.assist-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 22rpx;
  margin-top: 30rpx;
  color: #0899a5;
  font-size: 27rpx;
  font-weight: 600;
}

.assist-divider {
  width: 2rpx;
  height: 24rpx;
  background: #ccecef;
}
</style>

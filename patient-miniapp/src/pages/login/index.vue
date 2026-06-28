<template>
  <patient-nav-bar title="患者登录" />
  <view class="page login-page">
    <view class="login-card">
      <view class="tabs">
        <text
          v-for="item in modeTabs"
          :key="item.value"
          :class="{ active: mode === item.value }"
          @tap="switchMode(item.value)"
        >
          {{ item.label }}
        </text>
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
        placeholder="请输入密码"
      />

      <input
        v-if="mode === 'REGISTER'"
        v-model="name"
        class="input"
        placeholder="请输入姓名"
      />

      <view v-if="mode !== 'PASSWORD'" class="code-row">
        <input
          v-model="smsCode"
          class="input code-input"
          type="number"
          placeholder="请输入短信验证码"
        />
      </view>

      <view class="actions">
        <text
          v-for="item in actionTabs"
          :key="item.value"
          :class="item.value === 'SEND_CODE' ? 'action-link secondary-action' : 'action-link primary-action'"
          @tap="handleAction(item.value)"
        >
          {{ item.label }}
        </text>
      </view>

    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useAuthStore } from '../../stores/auth';

type Mode = 'PASSWORD' | 'SMS' | 'REGISTER' | 'RESET';
type ActionType = 'SEND_CODE' | 'SUBMIT';
type SmsPurpose = 'REGISTER' | 'LOGIN' | 'RESET_PASSWORD';

const auth = useAuthStore();
const mode = ref<Mode>('PASSWORD');
const phone = ref('13800000011');
const password = ref('abc12345');
const name = ref('新患者');
const smsCode = ref('');

const modeTabs = [
  { value: 'PASSWORD' as Mode, label: '密码登录' },
  { value: 'SMS' as Mode, label: '验证码登录' },
  { value: 'REGISTER' as Mode, label: '注册' },
  { value: 'RESET' as Mode, label: '找回密码' }
];

const submitLabel = computed(
  () =>
    ({
      PASSWORD: '登录',
      SMS: '验证码登录',
      REGISTER: '注册',
      RESET: '重置密码'
    })[mode.value]
);

const actionTabs = computed(() => {
  const actions: Array<{ value: ActionType; label: string }> = [];
  if (mode.value !== 'PASSWORD') {
    actions.push({ value: 'SEND_CODE', label: '获取验证码' });
  }
  actions.push({ value: 'SUBMIT', label: submitLabel.value });
  return actions;
});

function switchMode(nextMode: Mode) {
  mode.value = nextMode;
}

function handleAction(action: ActionType) {
  if (action === 'SEND_CODE') {
    void sendCode();
    return;
  }

  void submit();
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
  padding-top: 0;
  background: #f2f7ff;
}

.login-card {
  padding: 36rpx 30rpx;
  border-radius: 24rpx;
  background: #fff;
  box-shadow: 0 14rpx 38rpx rgba(31, 84, 140, 0.12);
}

.tabs {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12rpx;
  margin: 32rpx 0 22rpx;
  padding: 10rpx;
  border-radius: 16rpx;
  background: #f1f6fd;
}

.tabs text {
  padding: 16rpx 8rpx;
  border-radius: 12rpx;
  color: #64748b;
  font-size: 27rpx;
  font-weight: 700;
  text-align: center;
}

.tabs .active {
  background: #fff;
  color: #2f80ed;
  box-shadow: 0 6rpx 18rpx rgba(47, 128, 237, 0.14);
}

.phone-row,
.code-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.phone-prefix {
  min-width: 88rpx;
  padding: 22rpx 18rpx;
  border: 1px solid #d9e6f6;
  border-radius: 12rpx;
  background: #f8fbff;
  color: #1f2937;
  font-size: 30rpx;
  text-align: center;
  box-sizing: border-box;
}

.phone-input,
.code-input {
  flex: 1;
}

.actions {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  margin-top: 24rpx;
}

.action-link {
  display: block;
  padding: 22rpx 20rpx;
  border-radius: 12rpx;
  text-align: center;
  font-size: 32rpx;
  font-weight: 800;
  box-sizing: border-box;
}

.primary-action {
  background: linear-gradient(135deg, #4aa5ff 0%, #2f80ed 100%);
  color: #ffffff;
}

.secondary-action {
  background: #eef6ff;
  color: #2f80ed;
  border: 1px solid #cfe5ff;
}

</style>

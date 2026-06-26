<template>
  <view class="page">
    <view class="card">
      <view class="title">患者微信小程序</view>
      <view class="muted">注册、登录后进行 AI 智能问诊和线上挂号</view>

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

      <input v-model="phone" class="input" type="number" placeholder="手机号" />

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
        placeholder="姓名"
      />

      <view v-if="mode !== 'PASSWORD'" class="code-row">
        <input
          v-model="smsCode"
          class="input code-input"
          type="number"
          placeholder="短信验证码"
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

      <view v-if="devCode" class="muted">开发环境验证码：{{ devCode }}</view>
      <view class="muted">接口地址：{{ apiBaseUrl }}</view>
      <view class="debug">调试状态：{{ debugMessage }}</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { API_BASE_URL } from '../../api/http';
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
const devCode = ref('');
const apiBaseUrl = API_BASE_URL;
const debugMessage = ref('页面已加载，等待操作');

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

onShow(() => {
  debugMessage.value = `页面显示成功，当前模式：${mode.value}`;
  console.log('login page onShow', { apiBaseUrl, mode: mode.value });
});

function switchMode(nextMode: Mode) {
  mode.value = nextMode;
  debugMessage.value = `已切换到${modeTabs.find((item) => item.value === nextMode)?.label}`;
  console.log('login switchMode', { nextMode });
}

function handleAction(action: ActionType) {
  console.log('login handleAction', { action, mode: mode.value });
  if (action === 'SEND_CODE') {
    debugMessage.value = '已触发获取验证码点击事件';
    void sendCode();
    return;
  }

  debugMessage.value = `已触发${submitLabel.value}点击事件`;
  void submit();
}

function navigateToHome(successMessage: string) {
  debugMessage.value = `${successMessage}，准备跳转首页`;
  console.log('login navigateToHome', { mode: mode.value });
  uni.showToast({ title: successMessage, icon: 'success' });
  setTimeout(() => {
    uni.reLaunch({ url: '/pages/home/index' });
  }, 200);
}

async function sendCode() {
  debugMessage.value = `开始请求验证码，模式=${mode.value}，手机号=${phone.value}`;
  console.log('login sendCode start', { mode: mode.value, phone: phone.value });

  try {
    const purpose: SmsPurpose =
      mode.value === 'REGISTER'
        ? 'REGISTER'
        : mode.value === 'RESET'
          ? 'RESET_PASSWORD'
          : 'LOGIN';
    const result = await auth.sendCode(phone.value, purpose);
    devCode.value = result.devCode ?? '';
    if (result.devCode) {
      smsCode.value = result.devCode;
    }
    debugMessage.value = `验证码请求成功，expiresIn=${result.expiresIn}`;
  } catch (error) {
    debugMessage.value = `验证码请求失败：${(error as Error).message}`;
    uni.showToast({ title: (error as Error).message, icon: 'none' });
  }
}

async function submit() {
  debugMessage.value = `开始请求，模式=${mode.value}`;

  try {
    if (mode.value === 'PASSWORD') {
      await auth.login(phone.value, password.value);
      navigateToHome('密码登录成功');
      return;
    }

    if (mode.value === 'SMS') {
      await auth.smsLogin(phone.value, smsCode.value);
      navigateToHome('验证码登录成功');
      return;
    }

    if (mode.value === 'REGISTER') {
      await auth.register(phone.value, password.value, name.value, smsCode.value);
      navigateToHome('注册成功');
      return;
    }

    await auth.resetPassword(phone.value, smsCode.value, password.value);
    debugMessage.value = '密码重置成功，请使用新密码登录';
    uni.showToast({ title: '密码已重置', icon: 'success' });
    mode.value = 'PASSWORD';
  } catch (error) {
    debugMessage.value = `请求失败：${(error as Error).message}`;
    uni.showToast({ title: (error as Error).message, icon: 'none' });
  }
}
</script>

<style scoped>
.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 20rpx;
  margin: 28rpx 0 18rpx;
}

.tabs text {
  color: #64748b;
}

.tabs .active {
  color: #0f766e;
  font-weight: 700;
}

.code-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

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
  padding: 24rpx 20rpx;
  border-radius: 16rpx;
  text-align: center;
  font-size: 34rpx;
  font-weight: 700;
  box-sizing: border-box;
}

.primary-action {
  background: #0f766e;
  color: #ffffff;
}

.secondary-action {
  background: #f8fafc;
  color: #0f766e;
  border: 1px solid #99f6e4;
}

.debug {
  margin-top: 12rpx;
  color: #b45309;
  font-size: 24rpx;
  line-height: 1.6;
  word-break: break-all;
}
</style>

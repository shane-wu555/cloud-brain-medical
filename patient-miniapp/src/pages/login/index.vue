<template>
  <view class="page">
    <view class="card">
      <view class="title">患者微信小程序</view>
      <view class="muted">登录后进行智能问诊、挂号、缴费和报告查询</view>
      <input v-model="phone" class="input" type="number" placeholder="手机号" />
      <input v-model="password" class="input" password placeholder="8 位字母和数字密码" />
      <input v-if="registerMode" v-model="name" class="input" placeholder="真实姓名" />
      <input v-if="registerMode" v-model="smsCode" class="input" type="number" placeholder="短信验证码" />
      <button class="button" :loading="loading" @click="submit">{{ registerMode ? '注册' : '登录' }}</button>
      <button class="plain" @click="registerMode = !registerMode">{{ registerMode ? '已有账号，去登录' : '没有账号，去注册' }}</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useAuthStore } from '../../stores/auth';

const auth = useAuthStore();
const registerMode = ref(false);
const loading = ref(false);
const phone = ref('13800000000');
const password = ref('abc12345');
const name = ref('新患者');
const smsCode = ref('000000');

async function submit() {
  loading.value = true;
  try {
    if (registerMode.value) await auth.register(phone.value, password.value, name.value, smsCode.value);
    else await auth.login(phone.value === '13800000000' ? 'patient' : phone.value, password.value);
    uni.reLaunch({ url: '/pages/home/index' });
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' });
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>.plain { margin-top: 16rpx; background: transparent; color: #0f766e; font-size: 26rpx; }</style>

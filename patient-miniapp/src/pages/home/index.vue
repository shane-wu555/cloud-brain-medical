<template>
  <view class="page">
    <view class="card">
      <view class="title">你好，{{ auth.user?.name }}</view>
      <view class="muted">{{ auth.user?.realNameVerified ? '已实名认证' : '请先完成实名认证' }}</view>
      <button v-if="!auth.user?.realNameVerified" class="button" @click="go('/pages/real-name/index')">立即认证</button>
    </view>
    <view class="grid">
      <button class="entry" @click="go('/pages/consultation/index')">AI 智能问诊</button>
      <button class="entry" @click="go('/pages/booking/index')">线上挂号</button>
      <button class="entry" @click="go('/pages/appointments/index')">我的挂号</button>
      <button class="entry">检查检验报告</button>
      <button class="entry">电子病历</button>
      <button class="entry">待缴费用</button>
      <button class="entry">取药状态</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app';
import { useAuthStore } from '../../stores/auth';
const auth = useAuthStore();
onShow(async () => { if (!auth.token) { uni.reLaunch({ url: '/pages/login/index' }); return; } try { await auth.loadProfile(); } catch {} });
function go(url: string) {
  if (url === '/pages/booking/index' && !auth.user?.realNameVerified) { uni.showToast({ title: '请先完成实名认证', icon: 'none' }); return; }
  uni.navigateTo({ url });
}
</script>

<style scoped>.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20rpx; }.entry { height: 160rpx; display: grid; place-items: center; background: #fff; color: #0f766e; font-weight: 600; }</style>

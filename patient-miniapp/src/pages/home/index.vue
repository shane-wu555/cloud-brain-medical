<template>
  <view class="page home-page">
    <view class="hero card">
      <view class="hero-top">
        <view>
          <view class="title">你好，{{ auth.user?.username || auth.user?.phone || auth.user?.name }}</view>
          <view class="hero-subtitle">{{ auth.boundPatient ? `当前就诊人：${auth.boundPatient.name}` : '未绑定就诊人' }}</view>
        </view>
        <view :class="['verify-chip', auth.boundPatient ? 'verified' : 'unverified']">
          {{ auth.boundPatient ? '已绑定' : '待绑定' }}
        </view>
      </view>

      <view v-if="!auth.boundPatient" class="verify-panel">
        <view class="muted">添加并绑定就诊人后才能使用问诊、挂号、缴费和病历等业务。</view>
        <button class="button verify-button" @tap="go('/pages/real-name/index')">添加就诊人</button>
      </view>
      <view v-else class="verify-panel">
        <view class="muted">{{ auth.boundPatient.name }} · {{ auth.boundPatient.idNumber }}</view>
        <button class="button verify-button" @tap="go('/pages/real-name/index')">切换就诊人</button>
      </view>
    </view>

    <view class="card">
      <view class="section-title">快捷操作</view>
      <view class="quick-grid">
        <view
          v-for="item in quickEntries"
          :key="item.url"
          :class="['quick-card', item.tone]"
          @tap="go(item.url)"
        >
          <view class="quick-badge">{{ item.badge }}</view>
          <view class="quick-name">{{ item.name }}</view>
          <view class="quick-desc">{{ item.desc }}</view>
        </view>
      </view>
    </view>

    <view v-for="group in serviceGroups" :key="group.title" class="card">
      <view class="section-head">
        <view class="section-title">{{ group.title }}</view>
        <view class="muted">{{ group.subtitle }}</view>
      </view>

      <view
        v-for="item in group.items"
        :key="item.url"
        class="service-row"
        @tap="go(item.url)"
      >
        <view class="service-main">
          <view class="service-name">{{ item.name }}</view>
          <view class="service-desc">{{ item.desc }}</view>
        </view>
        <view class="service-arrow">{{ '>' }}</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app';
import { useAuthStore } from '../../stores/auth';

const auth = useAuthStore();

const quickEntries = [
  { name: 'AI 智能问诊', desc: '先问诊，再带着推荐去挂号', badge: 'AI', tone: 'tone-ai', url: '/pages/consultation/index' },
  { name: '线上挂号', desc: '选科室、选日期，直接预约医生', badge: '挂号', tone: 'tone-booking', url: '/pages/booking/index' },
  { name: '待缴费项目', desc: '集中处理挂号费、检查费、药费', badge: '缴费', tone: 'tone-payment', url: '/pages/pending-payments/index' }
];

const serviceGroups = [
  {
    title: '就诊服务',
    subtitle: '从挂号到处置、取药',
    items: [
      { name: '我的挂号', desc: '查看预约、就诊日期和当前状态', url: '/pages/appointments/index' },
      { name: '处置安排', desc: '查看换药、注射、输液等处置项目进度', url: '/pages/disposals/index' },
      { name: '药方', desc: '查看处方、药房状态、发药与退药信息', url: '/pages/prescriptions/index' }
    ]
  },
  {
    title: '健康档案',
    subtitle: '病历与正式报告',
    items: [
      { name: '电子病历', desc: '查看门诊病历摘要与历史就诊记录', url: '/pages/medical-records/index' },
      { name: '检查检验报告', desc: '仅查看已确认的检查与检验正式报告', url: '/pages/medical-orders/index' }
    ]
  }
];

onShow(async () => {
  if (!auth.token) {
    uni.reLaunch({ url: '/pages/login/index' });
    return;
  }

  try {
    await auth.loadProfile();
  } catch {
    // ignore profile refresh failure
  }
});

function go(url: string) {
  if (url !== '/pages/real-name/index' && !auth.boundPatient) {
    uni.showToast({ title: '请先添加并绑定就诊人', icon: 'none', duration: 3000 });
    uni.navigateTo({ url: '/pages/real-name/index?prompt=needPatient' });
    return;
  }
  uni.navigateTo({ url });
}
</script>

<style scoped>
.home-page {
  background:
    radial-gradient(circle at top right, rgba(15, 118, 110, 0.1), transparent 220rpx),
    linear-gradient(180deg, #f4fbfa 0%, #f5f7fb 240rpx);
}

.hero {
  background: linear-gradient(135deg, #0f766e 0%, #115e59 100%);
  color: #f8fafc;
}

.hero-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20rpx;
}

.hero-subtitle {
  margin-top: 12rpx;
  color: rgba(248, 250, 252, 0.82);
  font-size: 26rpx;
  line-height: 1.6;
}

.verify-chip {
  padding: 10rpx 18rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  white-space: nowrap;
}

.verified {
  background: rgba(236, 253, 245, 0.18);
  color: #d1fae5;
}

.unverified {
  background: rgba(255, 247, 237, 0.18);
  color: #fde68a;
}

.verify-panel {
  margin-top: 28rpx;
  padding: 20rpx 22rpx;
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.12);
}

.verify-panel .muted {
  color: rgba(248, 250, 252, 0.82);
}

.verify-button {
  margin-top: 16rpx;
  background: #f8fafc;
  color: #0f766e;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 16rpx;
  margin-bottom: 12rpx;
}

.section-title {
  color: #0f172a;
  font-size: 32rpx;
  font-weight: 700;
}

.quick-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 18rpx;
  margin-top: 18rpx;
}

.quick-card {
  padding: 24rpx;
  border-radius: 18rpx;
}

.tone-ai {
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
}

.tone-booking {
  background: linear-gradient(135deg, #ecfeff 0%, #ccfbf1 100%);
}

.tone-payment {
  background: linear-gradient(135deg, #fff7ed 0%, #ffedd5 100%);
}

.quick-badge {
  display: inline-flex;
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.9);
  color: #0f766e;
  font-size: 22rpx;
  font-weight: 700;
}

.quick-name {
  margin-top: 18rpx;
  color: #0f172a;
  font-size: 34rpx;
  font-weight: 700;
}

.quick-desc {
  margin-top: 10rpx;
  color: #475569;
  font-size: 26rpx;
  line-height: 1.6;
}

.service-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx 0;
  border-bottom: 1px solid #e2e8f0;
}

.service-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.service-main {
  flex: 1;
}

.service-name {
  color: #0f172a;
  font-size: 30rpx;
  font-weight: 700;
}

.service-desc {
  margin-top: 8rpx;
  color: #64748b;
  font-size: 25rpx;
  line-height: 1.6;
}

.service-arrow {
  color: #94a3b8;
  font-size: 40rpx;
}

</style>

<template>
  <view class="page home-page">
    <view class="top-band">
      <view class="hospital-name">智慧云脑诊疗平台</view>
      <view class="search-box" @tap="go('/pages/search/index')">
        <view class="search-icon"></view>
        <text>搜索科室、医生</text>
      </view>
    </view>

    <view class="banner-card">
      <view>
        <view class="banner-title">门诊预约与缴费一站办理</view>
        <view class="banner-desc">先问诊、再挂号，报告和费用集中查看</view>
      </view>
      <view class="banner-pill">轮播图待替换</view>
    </view>

    <view :class="['patient-card', auth.boundPatient ? 'bound' : 'unbound']">
      <view>
        <view class="patient-line">
          {{ auth.boundPatient ? `${auth.boundPatient.name} 的电子就诊卡` : '请先添加就诊人' }}
        </view>
        <view class="patient-subtitle">
          {{ auth.boundPatient ? `门诊号：${auth.boundPatient.id}` : '绑定后可使用挂号、缴费、报告和病历服务' }}
        </view>
      </view>
      <button class="switch-button" @tap="go('/pages/real-name/index')">
        {{ auth.boundPatient ? '切换' : '添加' }}
      </button>
    </view>

    <view class="quick-grid">
        <view
          v-for="item in quickEntries"
          :key="item.url"
          :class="['quick-card', item.tone]"
          @tap="go(item.url)"
        >
        <view class="quick-icon">{{ item.badge }}</view>
        <view>
          <view class="quick-name">{{ item.name }}</view>
          <view class="quick-desc">{{ item.desc }}</view>
        </view>
        </view>
    </view>

    <view class="service-panel">
      <view class="tab-row">
        <view
          v-for="group in serviceGroups"
          :key="group.title"
          :class="['tab-item', activeGroup === group.title ? 'active' : '']"
          @tap="activeGroup = group.title"
        >
          {{ group.title }}
        </view>
      </view>

      <view class="service-grid">
        <view
          v-for="item in activeItems"
          :key="item.url"
          class="service-item"
          @tap="go(item.url)"
        >
          <view class="service-icon">{{ item.icon }}</view>
          <view class="service-name">{{ item.name }}</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import { useAuthStore } from '../../stores/auth';

const auth = useAuthStore();
const activeGroup = ref('门诊');

const quickEntries = [
  { name: 'AI问诊建议', desc: '智能推荐科室', badge: 'AI', tone: 'tone-disease', url: '/pages/consultation/index' },
  { name: '按科室挂号', desc: '选择院区科室', badge: '科', tone: 'tone-dept', url: '/pages/booking/index' },
  { name: '就诊人管理', desc: '切换电子就诊卡', badge: '人', tone: 'tone-report', url: '/pages/real-name/index' },
  { name: '门诊缴费', desc: '挂号、药品等缴费', badge: '费', tone: 'tone-payment', url: '/pages/pending-payments/index' }
];

const serviceGroups = [
  {
    title: '门诊',
    items: [
      { name: '我的挂号', icon: '挂', url: '/pages/appointments/index' },
      { name: '待处置安排', icon: '处', url: '/pages/disposals/index?mode=arrangement' },
      { name: '待检查/检验安排', icon: '检', url: '/pages/medical-orders/index?mode=arrangement' },
      { name: '待取药安排', icon: '药', url: '/pages/prescriptions/index?mode=arrangement' }
    ]
  },
  {
    title: '记录',
    items: [
      { name: '检查检验报告', icon: '报', url: '/pages/medical-orders/index?mode=report' },
      { name: '电子病历', icon: '历', url: '/pages/medical-records/index' },
      { name: '缴费记录', icon: '缴', url: '/pages/pending-payments/index?mode=record' },
      { name: '处置记录', icon: '处', url: '/pages/disposals/index?mode=record' },
      { name: '取药记录', icon: '药', url: '/pages/prescriptions/index?mode=record' }
    ]
  }
];

const activeItems = computed(() => serviceGroups.find((group) => group.title === activeGroup.value)?.items ?? serviceGroups[0].items);

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
  padding-top: 0;
  background: linear-gradient(180deg, #48a4f5 0, #48a4f5 256rpx, #f2f7ff 256rpx, #f2f7ff 100%);
}

.top-band {
  padding: 30rpx 6rpx 24rpx;
  color: #fff;
}

.hospital-name {
  font-size: 38rpx;
  font-weight: 800;
  line-height: 1.3;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 16rpx;
  height: 78rpx;
  margin-top: 24rpx;
  padding: 0 24rpx;
  border-radius: 14rpx;
  background: #fff;
  color: #9aa8ba;
  font-size: 30rpx;
  box-shadow: 0 8rpx 24rpx rgba(22, 91, 163, 0.18);
}

.search-icon {
  width: 26rpx;
  height: 26rpx;
  border: 4rpx solid #b6c2d1;
  border-radius: 50%;
  position: relative;
}

.search-icon::after {
  content: "";
  position: absolute;
  right: -12rpx;
  bottom: -10rpx;
  width: 16rpx;
  height: 4rpx;
  border-radius: 999rpx;
  background: #b6c2d1;
  transform: rotate(45deg);
}

.banner-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 184rpx;
  margin-bottom: 22rpx;
  padding: 30rpx;
  border-radius: 18rpx;
  background: linear-gradient(135deg, #ff8a00 0%, #ff5c35 100%);
  color: #fff;
  box-shadow: 0 14rpx 34rpx rgba(255, 117, 24, 0.24);
}

.banner-title {
  font-size: 42rpx;
  font-weight: 900;
  line-height: 1.3;
}

.banner-desc {
  margin-top: 12rpx;
  color: rgba(255, 255, 255, 0.86);
  font-size: 26rpx;
}

.banner-pill {
  flex-shrink: 0;
  padding: 12rpx 16rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.18);
  font-size: 24rpx;
  font-weight: 700;
}

.patient-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20rpx;
  margin-bottom: 22rpx;
  padding: 30rpx;
  border-radius: 18rpx;
  color: #fff;
  box-shadow: 0 12rpx 28rpx rgba(47, 128, 237, 0.18);
}

.patient-card.bound {
  background: linear-gradient(135deg, #1677ff 0%, #28d2c0 100%);
}

.patient-card.unbound {
  background: linear-gradient(135deg, #64748b 0%, #94a3b8 100%);
}

.patient-line {
  font-size: 34rpx;
  font-weight: 800;
}

.patient-subtitle {
  margin-top: 14rpx;
  color: rgba(255, 255, 255, 0.86);
  font-size: 26rpx;
}

.switch-button {
  flex-shrink: 0;
  height: 64rpx;
  min-width: 116rpx;
  margin: 0;
  border: 2rpx solid rgba(255, 255, 255, 0.8);
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.16);
  color: #fff;
  font-size: 26rpx;
  line-height: 60rpx;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18rpx;
  margin-bottom: 22rpx;
}

.quick-card {
  display: flex;
  align-items: center;
  gap: 20rpx;
  min-height: 130rpx;
  padding: 24rpx;
  border-radius: 16rpx;
  background: #fff;
  box-shadow: 0 10rpx 26rpx rgba(31, 84, 140, 0.08);
}

.quick-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 78rpx;
  height: 78rpx;
  border-radius: 20rpx;
  color: #fff;
  font-size: 30rpx;
  font-weight: 900;
}

.tone-disease .quick-icon {
  background: linear-gradient(135deg, #ffc928 0%, #ff8a00 100%);
}

.tone-dept .quick-icon {
  background: linear-gradient(135deg, #ff9f1c 0%, #ff5c35 100%);
}

.tone-report .quick-icon {
  background: linear-gradient(135deg, #5bbcff 0%, #2f80ed 100%);
}

.tone-payment .quick-icon {
  background: linear-gradient(135deg, #2dd4bf 0%, #0ea5e9 100%);
}

.quick-name {
  color: #0f172a;
  font-size: 31rpx;
  font-weight: 800;
}

.quick-desc {
  margin-top: 8rpx;
  color: #475569;
  font-size: 25rpx;
}

.service-panel {
  padding: 26rpx 24rpx 18rpx;
  border-radius: 18rpx;
  background: #fff;
  box-shadow: 0 10rpx 30rpx rgba(31, 84, 140, 0.08);
}

.tab-row {
  display: flex;
  justify-content: space-around;
  margin-bottom: 28rpx;
}

.tab-item {
  position: relative;
  padding-bottom: 16rpx;
  color: #2f3542;
  font-size: 34rpx;
  font-weight: 700;
}

.tab-item.active {
  color: #2f80ed;
}

.tab-item.active::after {
  content: "";
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 48rpx;
  height: 7rpx;
  border-radius: 999rpx;
  background: #2f80ed;
  transform: translateX(-50%);
}

.service-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  row-gap: 34rpx;
}

.service-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14rpx;
}

.service-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 76rpx;
  height: 76rpx;
  border-radius: 18rpx;
  background: linear-gradient(135deg, #37b3ff 0%, #2f80ed 68%, #ff9f1c 100%);
  color: #fff;
  font-size: 28rpx;
  font-weight: 900;
}

.service-name {
  color: #2f3542;
  font-size: 27rpx;
  font-weight: 600;
  text-align: center;
}
</style>

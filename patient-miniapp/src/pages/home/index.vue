<template>
  <patient-nav-bar title="智慧云脑诊疗" />
  <view class="page home-page">
    <view class="top-band">
      <view class="search-box" @tap="go('/pages/search/index')">
        <view class="search-icon"></view>
        <text>搜索科室、医生</text>
      </view>
    </view>

    <swiper class="banner-swiper" :indicator-dots="true" :autoplay="false" indicator-color="rgba(255, 255, 255, 0.72)" indicator-active-color="#48a4f5">
      <swiper-item>
        <image class="banner-image" src="/static/banners/home-banner.png" mode="aspectFill" />
      </swiper-item>
    </swiper>

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
        <view class="quick-icon">
          <MedicalIcon :name="item.icon" :size="34" />
        </view>
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
          <view class="service-icon" :style="{ background: item.iconBg }">
            <MedicalIcon :name="item.icon" :size="32" variant="white" />
          </view>
          <view class="service-name">{{ item.name }}</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import MedicalIcon from '../../components/MedicalIcon.vue';
import { useAuthStore } from '../../stores/auth';

const auth = useAuthStore();
const activeGroup = ref('门诊');

const quickEntries = [
  { name: 'AI问诊建议', desc: '智能推荐科室', icon: 'stethoscope', tone: 'tone-disease', url: '/pages/consultation/index' },
  { name: '按科室挂号', desc: '选择院区科室', icon: 'hospital', tone: 'tone-dept', url: '/pages/booking/index' },
  { name: '就诊人管理', desc: '切换电子就诊卡', icon: 'user-round-plus', tone: 'tone-report', url: '/pages/real-name/index' },
  { name: '门诊缴费', desc: '挂号药品等缴费', icon: 'wallet-cards', tone: 'tone-payment', url: '/pages/pending-payments/index' }
];

const serviceGroups = [
  {
    title: '门诊',
    items: [
      { name: '我的挂号', icon: 'calendar-days', iconBg: 'linear-gradient(135deg, #5bbcff 0%, #2f80ed 100%)', url: '/pages/appointments/index' },
      { name: '待处置安排', icon: 'syringe', iconBg: 'linear-gradient(135deg, #ff9b7a 0%, #ff5c35 100%)', url: '/pages/disposals/index?mode=arrangement' },
      { name: '待检查/检验安排', icon: 'microscope', iconBg: 'linear-gradient(135deg, #ffc928 0%, #ff9f1c 100%)', url: '/pages/medical-orders/index?mode=arrangement' },
      { name: '待取药安排', icon: 'pill-bottle', iconBg: 'linear-gradient(135deg, #34d399 0%, #10b981 100%)', url: '/pages/prescriptions/index?mode=arrangement' }
    ]
  },
  {
    title: '记录',
    items: [
      { name: '检查检验报告', icon: 'microscope', iconBg: 'linear-gradient(135deg, #ffc928 0%, #ff9f1c 100%)', url: '/pages/medical-orders/index?mode=report' },
      { name: '电子病历', icon: 'clipboard-list', iconBg: 'linear-gradient(135deg, #5bbcff 0%, #2f80ed 100%)', url: '/pages/medical-records/index' },
      { name: '缴费记录', icon: 'wallet-cards', iconBg: 'linear-gradient(135deg, #2dd4bf 0%, #0ea5e9 100%)', url: '/pages/pending-payments/index?mode=record' },
      { name: '处置记录', icon: 'syringe', iconBg: 'linear-gradient(135deg, #ff9b7a 0%, #ff5c35 100%)', url: '/pages/disposals/index?mode=record' },
      { name: '取药记录', icon: 'pill-bottle', iconBg: 'linear-gradient(135deg, #34d399 0%, #10b981 100%)', url: '/pages/prescriptions/index?mode=record' }
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
  padding: 24rpx 6rpx;
  color: #fff;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 16rpx;
  height: 78rpx;
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

.banner-swiper {
  height: 220rpx;
  margin-bottom: 22rpx;
  border-radius: 18rpx;
  overflow: hidden;
  box-shadow: 0 14rpx 34rpx rgba(31, 84, 140, 0.12);
}

.banner-image {
  display: block;
  width: 100%;
  height: 220rpx;
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
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
  min-height: 148rpx;
  padding: 28rpx 24rpx;
  border-radius: 18rpx;
  background: #fff;
  box-shadow: 0 12rpx 28rpx rgba(31, 84, 140, 0.1);
  overflow: hidden;
}

.quick-card::after {
  content: "";
  position: absolute;
  right: -22rpx;
  bottom: -32rpx;
  width: 128rpx;
  height: 128rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.55);
}

.quick-icon {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 84rpx;
  height: 84rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.62);
  box-shadow: inset 0 -6rpx 12rpx rgba(47, 128, 237, 0.08), 0 8rpx 18rpx rgba(31, 84, 140, 0.08);
  order: 2;
}

.tone-disease {
  background: linear-gradient(135deg, #fff6d9 0%, #ffe3b0 100%);
}

.tone-dept {
  background: linear-gradient(135deg, #e7f5ff 0%, #c7e8ff 100%);
}

.tone-report {
  background: linear-gradient(135deg, #e9fff4 0%, #c8f5de 100%);
}

.tone-payment {
  background: linear-gradient(135deg, #fff0e8 0%, #ffd9cb 100%);
}

.quick-name {
  position: relative;
  z-index: 1;
  color: #143450;
  font-size: 34rpx;
  font-weight: 900;
  line-height: 1.2;
}

.quick-desc {
  position: relative;
  z-index: 1;
  margin-top: 8rpx;
  color: #718096;
  font-size: 26rpx;
  font-weight: 500;
  line-height: 1.35;
}

.service-panel {
  overflow: hidden;
  padding: 0 0 22rpx;
  border: 2rpx solid #dbeafe;
  border-radius: 22rpx;
  background: #fff;
  box-shadow: 0 16rpx 38rpx rgba(31, 84, 140, 0.12);
}

.tab-row {
  display: flex;
  align-items: center;
  min-height: 86rpx;
  padding: 0;
  margin-bottom: 30rpx;
  background: #48a4f5;
}

.tab-item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  height: 86rpx;
  padding: 0;
  border-radius: 0;
  color: rgba(255, 255, 255, 0.92);
  font-size: 34rpx;
  font-weight: 700;
}

.tab-item.active {
  background: #fff;
  color: #48a4f5;
  box-shadow: none;
}

.tab-item:first-child.active {
  border-top-left-radius: 20rpx;
  border-top-right-radius: 20rpx;
}

.tab-item:last-child.active {
  border-top-left-radius: 20rpx;
  border-top-right-radius: 20rpx;
}

.tab-item.active::after {
  display: none;
}

.service-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  row-gap: 38rpx;
  padding: 0 18rpx;
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
  width: 92rpx;
  height: 92rpx;
  border-radius: 24rpx;
  background: #eef6ff;
  box-shadow: inset 0 -8rpx 14rpx rgba(47, 128, 237, 0.08), 0 8rpx 18rpx rgba(47, 128, 237, 0.08);
}

.service-name {
  color: #2f3542;
  font-size: 27rpx;
  font-weight: 600;
  text-align: center;
}
</style>

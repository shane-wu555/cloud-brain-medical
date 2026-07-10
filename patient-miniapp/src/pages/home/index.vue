<template>
  <patient-nav-bar title="智慧云脑诊疗" />
  <view class="page home-page">
    <view class="top-band">
      <view class="search-box" @tap="go('/pages/search/index')">
        <view class="search-icon"></view>
        <text>搜索科室、医生</text>
      </view>
    </view>

    <swiper class="banner-swiper" :indicator-dots="true" :autoplay="false" indicator-color="rgba(255, 255, 255, 0.72)" indicator-active-color="#0899a5">
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
          {{ auth.boundPatient ? '已绑定就诊人' : '绑定后可使用挂号、缴费、报告和病历服务' }}
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
          @tap="go(item.url, item.readCategories)"
        >
        <view class="quick-icon">
          <MedicalIcon :name="item.icon" :size="34" />
          <view v-if="item.readCategories && quickBadgeCount(item.readCategories) > 0" class="card-badge">
            <text>{{ quickBadgeCount(item.readCategories) }}</text>
          </view>
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

      <view
        v-for="group in serviceGroups"
        :key="`${group.title}-panel`"
        :class="['service-grid', activeGroup === group.title ? 'is-active' : 'is-hidden']"
      >
        <view
          v-for="item in group.items"
          :key="item.url"
          class="service-item"
          @tap="go(item.url, item.readCategories)"
        >
          <view class="service-icon" :style="{ background: item.iconBg }">
            <MedicalIcon :name="item.icon" :size="32" variant="white" />
            <view v-if="item.readCategories && serviceBadgeCount(item.readCategories) > 0" class="service-badge">
              <text>{{ serviceBadgeCount(item.readCategories) }}</text>
            </view>
          </view>
          <view class="service-name">{{ item.name }}</view>
        </view>
      </view>
    </view>
  </view>

  <patient-tab-bar current="home" />
</template>

<script setup lang="ts">
import { onHide, onShow } from '@dcloudio/uni-app';
import { ref } from 'vue';
import MedicalIcon from '../../components/MedicalIcon.vue';
import type { MedicalIconName } from '../../constants/medical-icons';
import { markAllRead } from '../../api/notification';
import { useAuthStore } from '../../stores/auth';
import { useNotificationStore } from '../../stores/notification';

interface QuickEntry {
  name: string;
  desc: string;
  icon: MedicalIconName;
  tone: string;
  url: string;
  readCategories?: string[];
}

interface ServiceItem {
  name: string;
  icon: MedicalIconName;
  iconBg: string;
  url: string;
  readCategories?: string[];
}

interface ServiceGroup {
  title: string;
  items: ServiceItem[];
}

const auth = useAuthStore();
const notifStore = useNotificationStore();
const activeGroup = ref('门诊');

let pollTimer: ReturnType<typeof setInterval> | null = null;

const quickEntries: QuickEntry[] = [
  { name: 'AI问诊建议', desc: '智能推荐科室', icon: 'stethoscope', tone: 'tone-disease', url: '/pages/consultation/index' },
  { name: '预约挂号', desc: '选择院区科室', icon: 'hospital', tone: 'tone-dept', url: '/pages/booking/index' },
  { name: '就诊人管理', desc: '切换电子就诊卡', icon: 'user-round-plus', tone: 'tone-report', url: '/pages/real-name/index' },
  { name: '门诊缴费', desc: '挂号药品等缴费', icon: 'wallet-cards', tone: 'tone-payment', url: '/pages/pending-payments/index', readCategories: ['PENDING_PAYMENT'] }
];

const serviceGroups: ServiceGroup[] = [
  {
    title: '门诊',
    items: [
      { name: '我的挂号', icon: 'calendar-days', iconBg: 'linear-gradient(135deg, #0cbdcc 0%, #0899a5 100%)', url: '/pages/appointments/index' },
      { name: '待处置安排', icon: 'syringe', iconBg: 'linear-gradient(135deg, #E88870 0%, #D06050 100%)', url: '/pages/disposals/index?mode=arrangement', readCategories: ['DISPOSAL_COMPLETED'] },
      { name: '待检查/检验安排', icon: 'microscope', iconBg: 'linear-gradient(135deg, #F0A860 0%, #E08840 100%)', url: '/pages/medical-orders/index?mode=arrangement', readCategories: ['EXAM_COMPLETED', 'REPORT_PUBLISHED', 'CALLED'] },
      { name: '待取药安排', icon: 'pill-bottle', iconBg: 'linear-gradient(135deg, #5CBF98 0%, #3DA878 100%)', url: '/pages/prescriptions/index?mode=arrangement', readCategories: ['DRUGS_DISPENSED'] }
    ]
  },
  {
    title: '记录',
    items: [
      { name: '检查检验报告', icon: 'microscope', iconBg: 'linear-gradient(135deg, #F0A860 0%, #E08840 100%)', url: '/pages/medical-orders/index?mode=report' },
      { name: '电子病历', icon: 'clipboard-list', iconBg: 'linear-gradient(135deg, #0cbdcc 0%, #0899a5 100%)', url: '/pages/medical-records/index' },
      { name: '缴费退费记录', icon: 'wallet-cards', iconBg: 'linear-gradient(135deg, #2dd4bf 0%, #0ea5e9 100%)', url: '/pages/pending-payments/index?mode=record' },
      { name: '处置记录', icon: 'syringe', iconBg: 'linear-gradient(135deg, #E88870 0%, #D06050 100%)', url: '/pages/disposals/index?mode=record' },
      { name: '取药退药记录', icon: 'pill-bottle', iconBg: 'linear-gradient(135deg, #5CBF98 0%, #3DA878 100%)', url: '/pages/prescriptions/index?mode=record' }
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

  await notifStore.refreshUnreadCount();

  if (pollTimer) clearInterval(pollTimer);
  pollTimer = setInterval(() => {
    notifStore.refreshUnreadCount();
  }, 30000);
});

onHide(() => {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
});

function quickBadgeCount(categories: string[]): number {
  let total = 0;
  for (const cat of categories) {
    total += notifStore.unreadByCategory[cat] || 0;
  }
  return total;
}

function serviceBadgeCount(categories: string[]): number {
  let total = 0;
  for (const cat of categories) {
    total += notifStore.unreadByCategory[cat] || 0;
  }
  return total;
}

function go(url: string, readCategories?: string[]) {
  if (url !== '/pages/real-name/index' && !auth.boundPatient) {
    uni.showToast({ title: '请先添加并绑定就诊人', icon: 'none', duration: 3000 });
    uni.navigateTo({ url: '/pages/real-name/index?prompt=needPatient' });
    return;
  }
  if (readCategories && readCategories.length > 0) {
    Promise.allSettled(readCategories.map((cat) => markAllRead(cat).catch(() => {})));
    notifStore.clearCategories(readCategories);
  }
  uni.navigateTo({ url });
}
</script>

<style scoped>
.home-page {
  padding-top: 0;
  padding-bottom: 140rpx;
  background: linear-gradient(
    180deg,
    var(--patient-theme) 0%,
    var(--patient-theme-strong) 160rpx,
    var(--patient-theme-soft-alt) 320rpx,
    var(--patient-theme-page-bg) 480rpx,
    var(--patient-theme-page-bg) 100%
  );
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
  box-shadow: 0 8rpx 26rpx rgba(80, 100, 95, 0.10);
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
  box-shadow: 0 12rpx 30rpx rgba(80, 100, 95, 0.08);
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
  box-shadow: 0 12rpx 30rpx rgba(80, 100, 95, 0.08);
}

.patient-card.bound {
  background: linear-gradient(135deg, var(--patient-theme) 0%, var(--patient-theme-strong) 100%);
}

.patient-card.unbound {
  background: linear-gradient(135deg, #7A8B99 0%, #A3B0BC 100%);
}

.patient-line {
  font-size: 34rpx;
  font-weight: 700;
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
  box-shadow: 0 8rpx 22rpx rgba(80, 100, 95, 0.06);
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
  box-shadow: inset 0 -4rpx 10rpx rgba(80, 100, 95, 0.05), 0 6rpx 14rpx rgba(80, 100, 95, 0.06);
  order: 2;
}

.tone-disease {
  background: linear-gradient(135deg, #E6F5F2 0%, #CDE8E3 100%);
}

.tone-dept {
  background: linear-gradient(135deg, #E8F0FA 0%, #D1DFF2 100%);
}

.tone-report {
  background: linear-gradient(135deg, #EAF5EC 0%, #D3E8D9 100%);
}

.tone-payment {
  background: linear-gradient(135deg, #F5F0EA 0%, #EBE2D8 100%);
}

.quick-name {
  position: relative;
  z-index: 1;
  color: #143450;
  font-size: 34rpx;
  font-weight: 600;
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

/* Card badge (for quick grid items) */
.card-badge {
  position: absolute;
  top: -8rpx;
  right: -8rpx;
  min-width: 32rpx;
  height: 32rpx;
  padding: 0 8rpx;
  border-radius: 999rpx;
  background: #e74c3c;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
}

.card-badge text {
  color: #fff;
  font-size: 20rpx;
  font-weight: 700;
  line-height: 1;
}

.service-panel {
  overflow: hidden;
  padding: 0 0 22rpx;
  border: 2rpx solid var(--patient-theme-border);
  border-radius: 22rpx;
  background: #fff;
  box-shadow: 0 8rpx 22rpx rgba(80, 100, 95, 0.06);
}

.tab-row {
  display: flex;
  align-items: center;
  min-height: 86rpx;
  padding: 0;
  margin-bottom: 30rpx;
  background: var(--patient-theme-strong);
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
  font-weight: 600;
}

.tab-item.active {
  background: #fff;
  color: var(--patient-theme-strong);
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
  display: none;
  grid-template-columns: repeat(3, 1fr);
  row-gap: 28rpx;
  padding: 0 18rpx;
}

.service-grid.is-active {
  display: grid;
}

.service-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14rpx;
}

.service-icon {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 92rpx;
  height: 92rpx;
  border-radius: 24rpx;
  background: var(--patient-theme-soft);
  box-shadow: inset 0 -6rpx 12rpx rgba(80, 100, 95, 0.06), 0 6rpx 16rpx rgba(80, 100, 95, 0.06);
}

/* Service badge (for service grid items) */
.service-badge {
  position: absolute;
  top: -8rpx;
  right: -8rpx;
  min-width: 30rpx;
  height: 30rpx;
  padding: 0 6rpx;
  border-radius: 999rpx;
  background: #e74c3c;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1;
}

.service-badge text {
  color: #fff;
  font-size: 18rpx;
  font-weight: 700;
  line-height: 1;
}

.service-name {
  color: #2f3542;
  font-size: 27rpx;
  font-weight: 500;
}
</style>

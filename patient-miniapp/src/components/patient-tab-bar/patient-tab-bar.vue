<template>
  <view class="tab-bar">
    <view
      v-for="item in tabs"
      :key="item.key"
      :class="['tab-item', currentTab === item.key ? 'active' : '']"
      @tap="goTab(item.key)"
    >
      <image class="tab-icon" :src="currentTab === item.key ? item.activeIcon : item.icon" mode="aspectFit" />
      <text :class="['tab-label', currentTab === item.key ? 'active' : '']">{{ item.label }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  current: 'home' | 'records' | 'my';
}>();

const currentTab = computed(() => props.current);

const tabs = [
  {
    key: 'home' as const,
    label: '首页',
    icon: '/static/icons/home.svg',
    activeIcon: '/static/icons/white/home.svg',
    url: '/pages/home/index',
  },
  {
    key: 'records' as const,
    label: '就诊记录',
    icon: '/static/icons/calendar-days.svg',
    activeIcon: '/static/icons/white/calendar-days.svg',
    url: '/pages/visit-records/index',
  },
  {
    key: 'my' as const,
    label: '我的',
    icon: '/static/icons/user-round-plus.svg',
    activeIcon: '/static/icons/white/user-round-plus.svg',
    url: '/pages/my/index',
  },
];

function goTab(key: 'home' | 'records' | 'my') {
  if (key === currentTab.value) return;
  const target = tabs.find((t) => t.key === key);
  if (target) {
    uni.switchTab({ url: target.url });
  }
}
</script>

<style scoped>
.tab-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  justify-content: space-around;
  height: 110rpx;
  padding-bottom: env(safe-area-inset-bottom, 0);
  background: #fff;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.06);
  z-index: 999;
}

.tab-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6rpx;
  flex: 1;
  height: 100%;
  padding-top: 8rpx;
}

.tab-icon {
  width: 44rpx;
  height: 44rpx;
}

.tab-label {
  font-size: 22rpx;
  color: #9aa8ba;
  font-weight: 500;
}

.tab-label.active {
  color: var(--patient-theme-strong);
  font-weight: 600;
}
</style>

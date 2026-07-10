<template>
  <view class="tab-bar">
    <view
      v-for="item in tabs"
      :key="item.key"
      :class="['tab-item', currentTab === item.key ? 'active' : '']"
      @tap="goTab(item.key)"
    >
      <view class="tab-icon-wrap">
        <image class="tab-icon" :src="currentTab === item.key ? item.activeIcon : item.icon" mode="aspectFit" />
        <view v-if="item.key === 'home' && notifStore.unreadTotal > 0" class="tab-badge">
          <text v-if="notifStore.unreadTotal <= 99">{{ notifStore.unreadTotal }}</text>
          <text v-else>99+</text>
        </view>
      </view>
      <text :class="['tab-label', currentTab === item.key ? 'active' : '']">{{ item.label }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { getMedicalIcon } from '../../constants/medical-icons';
import { useAuthStore } from '../../stores/auth';
import { useNotificationStore } from '../../stores/notification';

const props = defineProps<{
  current: 'home' | 'records' | 'my';
}>();

const auth = useAuthStore();
const notifStore = useNotificationStore();
const currentTab = computed(() => props.current);

const tabs = [
  {
    key: 'home' as const,
    label: '首页',
    icon: getMedicalIcon('home'),
    activeIcon: getMedicalIcon('home', 'white'),
    url: '/pages/home/index',
  },
  {
    key: 'records' as const,
    label: '就诊记录',
    icon: getMedicalIcon('calendar-days'),
    activeIcon: getMedicalIcon('calendar-days', 'white'),
    url: '/pages/visit-records/index',
  },
  {
    key: 'my' as const,
    label: '我的',
    icon: getMedicalIcon('user-round-plus'),
    activeIcon: getMedicalIcon('user-round-plus', 'white'),
    url: '/pages/my/index',
  },
];

async function goTab(key: 'home' | 'records' | 'my') {
  if (key === currentTab.value) return;
  if (key === 'records') {
    if (auth.token) {
      try {
        await auth.loadProfile();
      } catch {
        // let the target page handle request failures
      }
    }
    if (!auth.boundPatient) {
      uni.showToast({ title: '请先添加并绑定就诊人', icon: 'none', duration: 3000 });
      uni.navigateTo({ url: '/pages/real-name/index?prompt=needPatient' });
      return;
    }
  }
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

.tab-icon-wrap {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.tab-icon {
  width: 44rpx;
  height: 44rpx;
}

.tab-badge {
  position: absolute;
  top: -10rpx;
  right: -16rpx;
  min-width: 32rpx;
  height: 32rpx;
  padding: 0 8rpx;
  border-radius: 999rpx;
  background: #e74c3c;
  display: flex;
  align-items: center;
  justify-content: center;
}

.tab-badge text {
  color: #fff;
  font-size: 20rpx;
  font-weight: 700;
  line-height: 1;
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

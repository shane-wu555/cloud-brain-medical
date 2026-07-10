<template>
  <patient-nav-bar title="我的" />
  <view class="page my-page">
    <!-- 用户信息卡片 -->
    <view class="user-card">
      <view class="avatar">
        <text class="avatar-text">{{ avatarText }}</text>
      </view>
      <view class="user-info">
        <view class="user-name">{{ auth.user?.name || '未登录' }}</view>
        <view class="user-phone">{{ maskedPhone }}</view>
      </view>
    </view>

    <!-- 当前就诊人 -->
    <view :class="['patient-card', auth.boundPatient ? 'bound' : 'unbound']" @tap="go('/pages/real-name/index')">
      <view>
        <view class="patient-name">
          {{ auth.boundPatient ? auth.boundPatient.name : '请先添加就诊人' }}
        </view>
        <view class="patient-detail" v-if="auth.boundPatient">
          {{ auth.boundPatient.gender === 'MALE' ? '男' : auth.boundPatient.gender === 'FEMALE' ? '女' : '' }}
          {{ auth.boundPatient.birthDate || '' }}
        </view>
      </view>
      <view class="arrow-icon"></view>
    </view>

    <!-- 功能菜单 -->
    <view class="menu-section">
      <view class="menu-item" @tap="go('/pages/profile/index')">
        <text>个人信息</text>
        <view class="arrow-icon"></view>
      </view>
      <view class="menu-item" @tap="go('/pages/appointments/index')">
        <text>我的挂号</text>
        <view class="arrow-icon"></view>
      </view>
      <view class="menu-item" @tap="go('/pages/medical-orders/index?mode=report')">
        <text>检查检验报告</text>
        <view class="arrow-icon"></view>
      </view>
      <view class="menu-item" @tap="go('/pages/medical-records/index')">
        <text>电子病历</text>
        <view class="arrow-icon"></view>
      </view>
      <view class="menu-item" @tap="go('/pages/pending-payments/index?mode=record')">
        <text>缴费退费记录</text>
        <view class="arrow-icon"></view>
      </view>
    </view>

    <!-- 退出登录 -->
    <view class="logout-btn" @tap="handleLogout">
      <text>退出登录</text>
    </view>
  </view>

  <patient-tab-bar current="my" />
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { useAuthStore } from '../../stores/auth';

const auth = useAuthStore();

const avatarText = computed(() => {
  return auth.user?.name ? auth.user.name.charAt(0).toUpperCase() : '?';
});

const maskedPhone = computed(() => {
  const phone = auth.user?.phone || '';
  if (phone.length === 11) {
    return phone.slice(0, 3) + '****' + phone.slice(7);
  }
  return phone;
});

onShow(async () => {
  if (!auth.token) {
    uni.reLaunch({ url: '/pages/login/index' });
    return;
  }
  try {
    await auth.loadProfile();
  } catch {
    // ignore
  }
});

function go(url: string) {
  if (url === '/pages/profile/index') {
    uni.navigateTo({ url });
    return;
  }
  if (url !== '/pages/real-name/index' && !auth.boundPatient) {
    uni.showToast({ title: '请先添加并绑定就诊人', icon: 'none', duration: 3000 });
    uni.navigateTo({ url: '/pages/real-name/index?prompt=needPatient' });
    return;
  }
  uni.navigateTo({ url });
}

function handleLogout() {
  uni.showModal({
    title: '提示',
    content: '确定要退出登录吗？',
    success: (res) => {
      if (res.confirm) {
        auth.logout();
        uni.reLaunch({ url: '/pages/login/index' });
      }
    }
  });
}
</script>

<style scoped>
.my-page {
  padding-bottom: 140rpx;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 36rpx 30rpx;
  margin-bottom: 22rpx;
  border-radius: 18rpx;
  background: linear-gradient(135deg, var(--patient-theme) 0%, var(--patient-theme-strong) 100%);
  box-shadow: 0 12rpx 30rpx rgba(80, 100, 95, 0.08);
}

.avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.28);
  flex-shrink: 0;
}

.avatar-text {
  color: #fff;
  font-size: 38rpx;
  font-weight: 700;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name {
  color: #fff;
  font-size: 36rpx;
  font-weight: 700;
}

.user-phone {
  margin-top: 8rpx;
  color: rgba(255, 255, 255, 0.8);
  font-size: 26rpx;
}

.patient-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 30rpx;
  margin-bottom: 22rpx;
  border-radius: 18rpx;
  background: #fff;
  box-shadow: 0 8rpx 22rpx rgba(80, 100, 95, 0.06);
}

.patient-card.bound {
  border-left: 6rpx solid var(--patient-theme-strong);
}

.patient-card.unbound {
  border-left: 6rpx solid #a0aec0;
}

.patient-name {
  color: #143450;
  font-size: 32rpx;
  font-weight: 600;
}

.patient-detail {
  margin-top: 8rpx;
  color: #718096;
  font-size: 26rpx;
}

.arrow-icon {
  width: 16rpx;
  height: 16rpx;
  border-top: 3rpx solid #c0c8d4;
  border-right: 3rpx solid #c0c8d4;
  transform: rotate(45deg);
  flex-shrink: 0;
}

.menu-section {
  border-radius: 18rpx;
  background: #fff;
  box-shadow: 0 8rpx 22rpx rgba(80, 100, 95, 0.06);
  overflow: hidden;
  margin-bottom: 22rpx;
}

.menu-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 30rpx;
  border-bottom: 1rpx solid var(--patient-theme-border);
  color: #143450;
  font-size: 30rpx;
}

.menu-item:last-child {
  border-bottom: none;
}

.logout-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  border-radius: 18rpx;
  background: #fff;
  color: #e53e3e;
  font-size: 30rpx;
  font-weight: 500;
  box-shadow: 0 8rpx 22rpx rgba(80, 100, 95, 0.06);
}
</style>

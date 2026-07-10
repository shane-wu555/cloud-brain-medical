<template>
  <patient-nav-bar title="个人信息" />
  <view class="page profile-page">
    <view class="section-card">
      <view class="section-title">账号信息</view>
      <view class="info-row">
        <text class="info-label">姓名</text>
        <text class="info-value">{{ auth.user?.name || '-' }}</text>
      </view>
      <view class="info-row">
        <text class="info-label">手机号</text>
        <text class="info-value">{{ maskedPhone }}</text>
      </view>
    </view>

    <view class="section-card">
      <view class="section-title">修改密码</view>
      <view class="form-group">
        <text class="form-label">当前密码</text>
        <input
          v-model="oldPassword"
          class="input"
          password
          placeholder="请输入当前密码"
        />
      </view>
      <view class="form-group">
        <text class="form-label">新密码</text>
        <input
          v-model="newPassword"
          class="input"
          password
          placeholder="请输入新密码（至少8位，含字母和数字）"
        />
      </view>
      <view class="form-group">
        <text class="form-label">确认新密码</text>
        <input
          v-model="confirmPassword"
          class="input"
          password
          placeholder="请再次输入新密码"
        />
      </view>
      <view
        :class="['save-btn', submitting ? 'save-btn--loading' : '']"
        hover-class="save-btn--hover"
        @tap="handleChangePassword()"
      >
        <text>{{ submitting ? '修改中...' : '确认修改' }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { useAuthStore } from '../../stores/auth';

const auth = useAuthStore();
const oldPassword = ref('');
const newPassword = ref('');
const confirmPassword = ref('');
const submitting = ref(false);

const maskedPhone = computed(() => {
  const phone = auth.user?.phone || '';
  if (phone.length === 11) {
    return phone.slice(0, 3) + '****' + phone.slice(7);
  }
  return phone;
});

onShow(() => {
  if (!auth.token) {
    uni.reLaunch({ url: '/pages/login/index' });
  }
});

async function handleChangePassword() {
  if (submitting.value) {
    return;
  }
  if (!oldPassword.value) {
    uni.showToast({ title: '请输入当前密码', icon: 'none' });
    return;
  }
  if (oldPassword.value.length < 8) {
    uni.showToast({ title: '当前密码至少8位', icon: 'none' });
    return;
  }
  if (!newPassword.value) {
    uni.showToast({ title: '请输入新密码', icon: 'none' });
    return;
  }
  if (newPassword.value.length < 8) {
    uni.showToast({ title: '新密码至少8位', icon: 'none' });
    return;
  }
  if (!/[A-Za-z]/.test(newPassword.value) || !/\d/.test(newPassword.value)) {
    uni.showToast({ title: '新密码需同时包含字母和数字', icon: 'none' });
    return;
  }
  if (newPassword.value !== confirmPassword.value) {
    uni.showToast({ title: '两次输入的新密码不一致', icon: 'none' });
    return;
  }

  submitting.value = true;
  try {
    await auth.changePassword(oldPassword.value, newPassword.value);
    uni.showToast({ title: '密码修改成功', icon: 'success' });
    oldPassword.value = '';
    newPassword.value = '';
    confirmPassword.value = '';
    setTimeout(() => {
      uni.navigateBack();
    }, 1200);
  } catch (e: any) {
    uni.showToast({ title: e?.message || '修改失败，请重试', icon: 'none' });
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.profile-page {
  position: relative;
  padding-bottom: 72rpx;
}

.section-card {
  margin-bottom: 24rpx;
  padding: 30rpx;
  border: 2rpx solid var(--patient-theme-border);
  border-radius: 18rpx;
  background: #fff;
  box-shadow: 0 8rpx 22rpx rgba(80, 100, 95, 0.06);
}

.section-title {
  margin-bottom: 24rpx;
  color: #0d3d5c;
  font-size: 32rpx;
  font-weight: 700;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #ecf7f8;
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  color: #64748b;
  font-size: 28rpx;
}

.info-value {
  color: #143450;
  font-size: 28rpx;
  font-weight: 500;
}

.form-group {
  margin-bottom: 22rpx;
}

.form-label {
  display: block;
  margin-bottom: 10rpx;
  color: #475569;
  font-size: 26rpx;
  font-weight: 500;
}

.form-group .input {
  margin-top: 0;
}

.save-btn {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  margin-top: 32rpx;
  border-radius: 14rpx;
  background: linear-gradient(135deg, var(--patient-theme) 0%, var(--patient-theme-strong) 100%);
  color: #fff;
  font-size: 30rpx;
  font-weight: 600;
  line-height: 88rpx;
  box-shadow: 0 10rpx 24rpx rgba(12, 189, 204, 0.2);
}

.save-btn--hover {
  opacity: 0.92;
}

.save-btn--loading {
  background: #cbd5e1;
  color: #fff;
  box-shadow: none;
}
</style>

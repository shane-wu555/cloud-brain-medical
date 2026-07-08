<template>
  <view class="patient-nav-bar">
    <view class="nav-inner">
      <view class="nav-side nav-left" @tap="handleBack()">
        <view v-if="canBack" class="back-icon"></view>
      </view>
      <view class="nav-title">{{ title }}</view>
      <view class="nav-side"></view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app';
import { onMounted, ref } from 'vue';

defineProps<{
  title: string;
}>();

const canBack = ref(false);

onMounted(updateCanBack);
onShow(updateCanBack);

function updateCanBack() {
  canBack.value = getCurrentPages().length > 1;
}

function handleBack() {
  if (!canBack.value) {
    return;
  }

  uni.navigateBack();
}
</script>

<style scoped>
.patient-nav-bar {
  padding-top: var(--status-bar-height, 0px);
  background: linear-gradient(135deg, var(--patient-theme) 0%, var(--patient-theme-strong) 100%);
  color: #fff;
}

.nav-inner {
  display: flex;
  align-items: center;
  height: 96rpx;
}

.nav-side {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 180rpx;
  height: 96rpx;
  flex: 0 0 180rpx;
}

.nav-title {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 36rpx;
  font-weight: 700;
  line-height: 1.2;
}

.back-icon {
  width: 22rpx;
  height: 22rpx;
  border-left: 4rpx solid #fff;
  border-bottom: 4rpx solid #fff;
  transform: rotate(45deg);
}
</style>

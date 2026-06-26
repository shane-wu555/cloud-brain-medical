<template>
  <view class="page">
    <view class="card">
      <view class="title">AI 智能问诊</view>
      <textarea v-model="description" class="input" placeholder="请描述症状、持续时间和伴随表现" />
      <button class="button" @click="consult">提交问诊</button>
      <view v-if="result" class="result">
        <view class="line">模型：{{ result.model }}{{ result.fallbackUsed ? ' / Mock 回退' : '' }}</view>
        <view class="line">风险等级：{{ result.riskLevel }}</view>
        <view class="line">推荐科室：{{ result.recommendedDepartmentName }}</view>
        <view class="line">{{ result.recordDraft }}</view>
        <button class="button" @click="goBooking">按推荐去挂号</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { request } from '../../api/http';
import { useAuthStore } from '../../stores/auth';

interface ConsultationResponse {
  aiRecordId: string;
  summary: string;
  riskLevel: string;
  recommendedDepartmentId: string;
  recommendedDepartmentName: string;
  recommendedDoctors: Array<{ doctorId: string; doctorName: string; reason: string }>;
  recordDraft: string;
  provider: string;
  model: string;
  fallbackUsed: boolean;
}

const description = ref('');
const result = ref<ConsultationResponse>();
const auth = useAuthStore();

async function consult() {
  await auth.loadProfile();
  try {
    auth.requireBoundPatient();
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' });
    uni.navigateTo({ url: '/pages/real-name/index' });
    return;
  }
  result.value = await request<ConsultationResponse>({
    url: '/ai/consultations',
    method: 'POST',
    data: { description: description.value, symptomTags: [] }
  });
  uni.setStorageSync('last_ai_consultation', result.value);
}

function goBooking() {
  uni.navigateTo({ url: '/pages/booking/index?fromAi=1' });
}
</script>

<style scoped>
.result { margin-top: 24rpx; line-height: 1.7; }
.line { margin-bottom: 12rpx; }
</style>

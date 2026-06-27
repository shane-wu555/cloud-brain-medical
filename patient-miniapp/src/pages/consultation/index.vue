<template>
  <view class="page consultation-page">
    <view class="consult-hero">
      <view class="hero-title">AI 智能问诊</view>
      <view class="hero-subtitle">请描述症状、持续时间和伴随表现，AI 将整理问诊建议和推荐科室</view>
    </view>

    <view class="card consult-card">
      <view class="title">症状描述</view>
      <textarea
        v-model="description"
        class="symptom-input"
        maxlength="1000"
        placeholder="例如：头痛 2 天，伴有恶心，夜间加重……"
        placeholder-class="placeholder"
      />
      <button class="button" :disabled="loading" @tap="consult()">
        {{ loading ? '问诊中...' : '提交问诊' }}
      </button>
      <view v-if="result" class="result">
        <view class="line">模型：{{ result.model }}{{ result.fallbackUsed ? ' / Mock 回退' : '' }}</view>
        <view class="line">风险等级：{{ result.riskLevel }}</view>
        <view class="line">推荐科室：{{ result.recommendedDepartmentName }}</view>
        <view class="line">{{ result.recordDraft }}</view>
        <button class="button" @tap="goBooking()">按推荐去挂号</button>
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
const loading = ref(false);
const auth = useAuthStore();

async function consult() {
  if (loading.value) {
    return;
  }
  if (!description.value.trim()) {
    uni.showToast({ title: '请先描述症状', icon: 'none' });
    return;
  }
  loading.value = true;
  try {
    await auth.loadProfile();
    auth.requireBoundPatient();
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' });
    uni.navigateTo({ url: '/pages/real-name/index' });
    loading.value = false;
    return;
  }
  try {
    result.value = await request<ConsultationResponse>({
      url: '/ai/consultations',
      method: 'POST',
      data: { description: description.value.trim(), symptomTags: [] }
    });
    uni.setStorageSync('last_ai_consultation', result.value);
    uni.showToast({ title: '问诊完成', icon: 'success' });
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' });
  } finally {
    loading.value = false;
  }
}

function goBooking() {
  uni.navigateTo({ url: '/pages/booking/index?fromAi=1' });
}
</script>

<style scoped>
.consultation-page {
  padding-top: 0;
  background: linear-gradient(180deg, #48a4f5 0, #48a4f5 210rpx, #f2f7ff 210rpx, #f2f7ff 100%);
}

.consult-hero {
  padding: 34rpx 4rpx 28rpx;
  color: #fff;
}

.hero-title {
  font-size: 46rpx;
  font-weight: 900;
}

.hero-subtitle {
  margin-top: 12rpx;
  color: rgba(255, 255, 255, 0.86);
  font-size: 27rpx;
  line-height: 1.55;
}

.consult-card {
  display: flex;
  flex-direction: column;
  gap: 22rpx;
}

.symptom-input {
  width: 100%;
  min-height: 260rpx;
  padding: 24rpx;
  border: 1px solid #d9e6f6;
  border-radius: 14rpx;
  background: #f8fbff;
  box-sizing: border-box;
  color: #1f2937;
  font-size: 30rpx;
  line-height: 1.6;
}

.placeholder {
  color: #a8b3c2;
}

.result {
  margin-top: 8rpx;
  padding-top: 22rpx;
  border-top: 1px solid #e8edf3;
  line-height: 1.7;
}

.line {
  margin-bottom: 12rpx;
  color: #334155;
  font-size: 28rpx;
}
</style>

<template>
  <view class="page">
    <view class="card">
      <view class="title">电子病历</view>
      <view class="muted">查看本次和历史就诊病历摘要</view>
    </view>

    <view v-for="record in visibleRecords" :key="record.id" class="card record-card">
      <view class="row-between">
        <view class="title-sm">{{ record.departmentName }} · {{ record.visitDate }} {{ record.period }}</view>
        <view :class="['status-tag', record.status.toLowerCase()]">{{ statusLabel(record.status) }}</view>
      </view>
      <view class="muted">接诊医生：{{ record.doctorName }}</view>
      <view v-if="record.aiTriageSummary" class="section">
        <view class="label">AI 问诊摘要</view>
        <view>{{ record.aiTriageSummary }}</view>
      </view>
      <view v-if="record.chiefComplaint" class="section">
        <view class="label">主诉</view>
        <view>{{ record.chiefComplaint }}</view>
      </view>
      <view v-if="record.presentIllness" class="section">
        <view class="label">现病史</view>
        <view>{{ record.presentIllness }}</view>
      </view>
      <view v-if="record.preliminaryDiagnosis" class="section">
        <view class="label">初步诊断</view>
        <view>{{ record.preliminaryDiagnosis }}</view>
      </view>
      <view v-if="record.treatmentPlan" class="section">
        <view class="label">处理意见</view>
        <view>{{ record.treatmentPlan }}</view>
      </view>
    </view>

    <view v-if="!visibleRecords.length" class="card muted">暂无电子病历</view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import { request } from '../../api/http';
import { useAuthStore } from '../../stores/auth';

interface MedicalRecord {
  id: string;
  appointmentId: string;
  patientId: string;
  patientName: string;
  doctorId: string;
  doctorName: string;
  departmentName: string;
  visitDate: string;
  period: string;
  aiTriageSummary: string;
  aiRiskLevel: string;
  chiefComplaint: string;
  presentIllness: string;
  pastHistory: string;
  allergyHistory: string;
  physicalExamination: string;
  preliminaryDiagnosis: string;
  diagnosis: string;
  treatmentPlan: string;
  doctorRevisionNote: string;
  status: 'DRAFT' | 'ACTIVE' | 'ARCHIVED';
  updatedAt: string;
}

const records = ref<MedicalRecord[]>([]);
const auth = useAuthStore();
const visibleRecords = computed(() =>
  [...records.value].sort((a, b) => medicalRecordSortTime(b).localeCompare(medicalRecordSortTime(a)))
);

function statusLabel(status: MedicalRecord['status']) {
  return {
    DRAFT: '待完善',
    ACTIVE: '就诊中',
    ARCHIVED: '已归档'
  }[status] ?? status;
}

function medicalRecordSortTime(record: MedicalRecord) {
  return record.updatedAt || record.visitDate || '';
}

onShow(async () => {
  await auth.loadProfile();
  try {
    auth.requireBoundPatient();
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' });
    uni.navigateTo({ url: '/pages/real-name/index' });
    return;
  }
  records.value = await request<MedicalRecord[]>({ url: '/medical-records', method: 'GET' });
});
</script>

<style scoped>
.record-card {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.row-between {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16rpx;
}

.title-sm {
  font-size: 32rpx;
  font-weight: 700;
}

.label {
  margin-bottom: 6rpx;
  color: #0f766e;
  font-size: 24rpx;
  font-weight: 600;
}

.section {
  line-height: 1.6;
}

.status-tag {
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  white-space: nowrap;
}

.draft {
  background: #fff7ed;
  color: #c2410c;
}

.active {
  background: #ecfeff;
  color: #0f766e;
}

.archived {
  background: #f1f5f9;
  color: #475569;
}
</style>

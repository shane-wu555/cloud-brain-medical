<template>
  <patient-nav-bar title="电子病历" />
  <view class="page">
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

    <view v-if="!visibleRecords.length" class="card muted">{{ emptyText }}</view>
  </view>
</template>

<script setup lang="ts">
import { onLoad, onShow } from '@dcloudio/uni-app';
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
const selectedAppointmentId = ref('');
const auth = useAuthStore();

const visibleRecords = computed(() => {
  const sorted = [...records.value].sort((a, b) => medicalRecordSortTime(b).localeCompare(medicalRecordSortTime(a)));
  if (!selectedAppointmentId.value) {
    return sorted;
  }
  return sorted.filter((record) => record.appointmentId === selectedAppointmentId.value);
});

const emptyText = computed(() => (selectedAppointmentId.value ? '暂无对应电子病历' : '暂无电子病历'));

function statusLabel(status: MedicalRecord['status']) {
  return {
    DRAFT: '就诊中',
    ACTIVE: '就诊中',
    ARCHIVED: '已就诊'
  }[status] ?? status;
}

function medicalRecordSortTime(record: MedicalRecord) {
  return record.updatedAt || record.visitDate || '';
}

onLoad((options) => {
  selectedAppointmentId.value = typeof options?.appointmentId === 'string'
    ? decodeURIComponent(options.appointmentId)
    : '';
});

onShow(async () => {
  if (!auth.token) {
    uni.reLaunch({ url: '/pages/login/index' });
    return;
  }

  try {
    await auth.loadProfile();
    const patient = auth.requireBoundPatient();
    const query = [`patientId=${encodeURIComponent(patient.id)}`];
    if (selectedAppointmentId.value) {
      query.push(`appointmentId=${encodeURIComponent(selectedAppointmentId.value)}`);
    }
    records.value = await request<MedicalRecord[]>({
      url: `/medical-records?${query.join('&')}`,
      method: 'GET'
    });
  } catch (error) {
    const message = (error as Error).message;
    records.value = [];
    if (message === '请先添加并绑定就诊人') {
      uni.showToast({ title: message, icon: 'none' });
      uni.navigateTo({ url: '/pages/real-name/index?prompt=needPatient' });
      return;
    }
    uni.showToast({ title: message || '加载失败', icon: 'none' });
  }
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

.draft,
.active {
  background: #ecfeff;
  color: #0f766e;
}

.archived {
  background: #f1f5f9;
  color: #475569;
}
</style>

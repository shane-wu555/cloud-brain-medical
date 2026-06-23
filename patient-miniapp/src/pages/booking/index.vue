<template>
  <view class="page">
    <view v-if="aiConsultation" class="card">
      <view class="title">AI 推荐挂号</view>
      <view class="muted">{{ aiConsultation.summary }}</view>
      <view>推荐科室：{{ aiConsultation.recommendedDepartmentName }}</view>
      <view>风险等级：{{ aiConsultation.riskLevel }}</view>
    </view>

    <view class="card">
      <view class="title">选择科室</view>
      <picker :range="departments" range-key="name" @change="selectDepartment">
        <view class="input">{{ selectedDepartment?.name ?? '请选择科室' }}</view>
      </picker>
    </view>

    <view v-for="schedule in sortedSchedules" :key="schedule.id" class="card schedule">
      <view><text class="doctor">{{ schedule.doctorName }}</text> / {{ schedule.workDate }} {{ schedule.period }}</view>
      <view class="muted">剩余号源：{{ schedule.available }}</view>
      <view v-if="recommendedDoctorIds.includes(schedule.doctorId)" class="tag">AI 推荐医生</view>
      <button class="button" :disabled="schedule.available <= 0" @click="book(schedule)">0.01 元模拟支付</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { request } from '../../api/http';
import { useAuthStore } from '../../stores/auth';

interface Department { id: string; name: string }
interface Schedule { id: string; doctorId: string; doctorName: string; departmentId: string; workDate: string; period: string; capacity: number; booked: number; locked: number; available: number; status: string }
interface Appointment { id: string }
interface AiConsultation {
  summary: string;
  riskLevel: string;
  recommendedDepartmentId: string;
  recommendedDepartmentName: string;
  recommendedDoctors: Array<{ doctorId: string; doctorName: string; reason: string }>;
  recordDraft: string;
}

const auth = useAuthStore();
const departments = ref<Department[]>([]);
const selectedDepartmentId = ref('');
const schedules = ref<Schedule[]>([]);
const aiConsultation = ref<AiConsultation>();
const selectedDepartment = computed(() => departments.value.find((item) => item.id === selectedDepartmentId.value));
const recommendedDoctorIds = computed(() => aiConsultation.value?.recommendedDoctors.map((item) => item.doctorId) ?? []);
const sortedSchedules = computed(() => [...schedules.value].sort((a, b) => {
  const ar = recommendedDoctorIds.value.includes(a.doctorId) ? 0 : 1;
  const br = recommendedDoctorIds.value.includes(b.doctorId) ? 0 : 1;
  return ar - br;
}));

async function loadSchedules() {
  schedules.value = await request<Schedule[]>({ url: `/schedules?departmentId=${selectedDepartmentId.value}`, method: 'GET' });
}
async function selectDepartment(event: { detail: { value: string } }) {
  selectedDepartmentId.value = departments.value[Number(event.detail.value)]?.id ?? '';
  await loadSchedules();
}
async function book(schedule: Schedule) {
  if (!auth.user?.realNameVerified) {
    uni.showToast({ title: '请先完成实名认证', icon: 'none' });
    return;
  }
  try {
    const appointment = await request<Appointment>({
      url: '/appointments',
      method: 'POST',
      data: {
        scheduleId: schedule.id,
        patientId: auth.user.id,
        patientName: auth.user.name,
        doctorId: schedule.doctorId,
        doctorName: schedule.doctorName,
        departmentId: schedule.departmentId,
        departmentName: selectedDepartment.value?.name,
        visitDate: schedule.workDate,
        period: schedule.period,
        source: 'AI',
        triageSummary: aiConsultation.value?.recordDraft || aiConsultation.value?.summary || '',
        riskLevel: aiConsultation.value?.riskLevel || 'LOW',
        recommendedDepartmentId: aiConsultation.value?.recommendedDepartmentId || selectedDepartmentId.value
      }
    });
    await request({
      url: '/payments/orders',
      method: 'POST',
      data: { businessType: 'APPOINTMENT', businessId: appointment.id, patientId: auth.user.id, amount: 0.01, paymentMethod: 'WECHAT_TEST' }
    });
    await request({
      url: '/payments/test-callback',
      method: 'POST',
      data: { businessType: 'APPOINTMENT', businessId: appointment.id, patientId: auth.user.id, channel: 'WECHAT', channelTradeNo: `wx-test-${appointment.id}-${Date.now()}` }
    });
    uni.showToast({ title: '挂号成功', icon: 'success' });
    await loadSchedules();
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' });
  }
}

onMounted(async () => {
  aiConsultation.value = uni.getStorageSync('last_ai_consultation') || undefined;
  departments.value = await request<Department[]>({ url: '/departments', method: 'GET' });
  selectedDepartmentId.value = aiConsultation.value?.recommendedDepartmentId || departments.value[0]?.id || '';
  if (!departments.value.some((item) => item.id === selectedDepartmentId.value)) selectedDepartmentId.value = departments.value[0]?.id ?? '';
  if (selectedDepartmentId.value) await loadSchedules();
});
</script>

<style scoped>
.schedule { display: flex; flex-direction: column; gap: 12rpx; }
.doctor { font-weight: 700; font-size: 32rpx; }
.tag { align-self: flex-start; padding: 4rpx 12rpx; border-radius: 999rpx; background: #ecfdf5; color: #047857; font-size: 24rpx; }
</style>

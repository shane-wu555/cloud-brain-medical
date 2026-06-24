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

    <view v-if="availableDates.length" class="card">
      <view class="title">选择日期</view>
      <picker :range="availableDates" @change="selectDate">
        <view class="input">{{ selectedDate || '请选择日期' }}</view>
      </picker>
    </view>

    <view v-for="schedule in filteredSchedules" :key="schedule.id" class="card schedule">
      <view class="schedule-head">
        <view>
          <view class="doctor">{{ schedule.doctorName }}</view>
          <view class="muted">
            {{ doctorInfo(schedule.doctorId) }}
          </view>
        </view>
        <view v-if="recommendedDoctorIds.includes(schedule.doctorId)" class="tag">AI 推荐</view>
      </view>

      <view>{{ schedule.workDate }} · {{ schedule.period }}</view>
      <view class="muted">剩余号源：{{ schedule.available }} / {{ schedule.capacity }}</view>
      <button class="button" :disabled="schedule.available <= 0" @click="book(schedule)">挂号并缴费</button>
    </view>

    <view v-if="selectedDepartmentId && !filteredSchedules.length" class="card muted">
      当前日期暂无可预约医生排班
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { request } from '../../api/http';
import { useAuthStore } from '../../stores/auth';

interface Department {
  id: string;
  name: string;
}

interface Doctor {
  id: string;
  name: string;
  title: string;
  departmentId: string;
  specialty: string;
}

interface Schedule {
  id: string;
  doctorId: string;
  doctorName: string;
  departmentId: string;
  workDate: string;
  period: string;
  capacity: number;
  booked: number;
  locked: number;
  available: number;
  status: string;
}

interface Appointment {
  id: string;
}

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
const doctors = ref<Doctor[]>([]);
const selectedDepartmentId = ref('');
const schedules = ref<Schedule[]>([]);
const selectedDate = ref('');
const aiConsultation = ref<AiConsultation>();

const selectedDepartment = computed(() => departments.value.find((item) => item.id === selectedDepartmentId.value));
const recommendedDoctorIds = computed(() => aiConsultation.value?.recommendedDoctors.map((item) => item.doctorId) ?? []);
const availableDates = computed(() => Array.from(new Set(schedules.value.map((item) => item.workDate))).sort());
const doctorMap = computed(() => new Map(doctors.value.map((item) => [item.id, item])));
const filteredSchedules = computed(() =>
  schedules.value
    .filter((item) => !selectedDate.value || item.workDate === selectedDate.value)
    .sort((a, b) => {
      const ar = recommendedDoctorIds.value.includes(a.doctorId) ? 0 : 1;
      const br = recommendedDoctorIds.value.includes(b.doctorId) ? 0 : 1;
      if (ar !== br) {
        return ar - br;
      }
      return a.period.localeCompare(b.period, 'zh-CN');
    })
);

function doctorInfo(doctorId: string) {
  const doctor = doctorMap.value.get(doctorId);
  if (!doctor) {
    return '医生信息加载中';
  }
  const extra = [doctor.title, doctor.specialty].filter(Boolean).join(' · ');
  return extra || '门诊医生';
}

function syncSelectedDate() {
  if (!availableDates.value.length) {
    selectedDate.value = '';
    return;
  }
  if (!availableDates.value.includes(selectedDate.value)) {
    selectedDate.value = availableDates.value[0];
  }
}

async function loadDepartmentResources() {
  if (!selectedDepartmentId.value) {
    schedules.value = [];
    doctors.value = [];
    selectedDate.value = '';
    return;
  }

  const [doctorList, scheduleList] = await Promise.all([
    request<Doctor[]>({ url: `/doctors?departmentId=${selectedDepartmentId.value}`, method: 'GET' }),
    request<Schedule[]>({ url: `/schedules?departmentId=${selectedDepartmentId.value}`, method: 'GET' })
  ]);

  doctors.value = doctorList;
  schedules.value = scheduleList;
  syncSelectedDate();
}

async function selectDepartment(event: { detail: { value: string } }) {
  selectedDepartmentId.value = departments.value[Number(event.detail.value)]?.id ?? '';
  await loadDepartmentResources();
}

function selectDate(event: { detail: { value: string } }) {
  selectedDate.value = availableDates.value[Number(event.detail.value)] ?? '';
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
      data: {
        businessType: 'APPOINTMENT',
        businessId: appointment.id,
        patientId: auth.user.id,
        amount: 0.01,
        paymentMethod: 'WECHAT_TEST'
      }
    });

    try {
      await request({
        url: '/payments/test-callback',
        method: 'POST',
        data: {
          businessType: 'APPOINTMENT',
          businessId: appointment.id,
          patientId: auth.user.id,
          channel: 'WECHAT',
          channelTradeNo: `wx-test-${appointment.id}-${Date.now()}`
        }
      });
      uni.showToast({ title: '挂号并缴费成功', icon: 'success' });
    } catch (paymentError) {
      console.error('booking payment fallback', paymentError);
      uni.showToast({ title: '已挂号，请前往待缴费项目完成支付', icon: 'none' });
      setTimeout(() => {
        uni.navigateTo({ url: '/pages/pending-payments/index' });
      }, 400);
    }

    await loadDepartmentResources();
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' });
  }
}

async function initialize() {
  aiConsultation.value = uni.getStorageSync('last_ai_consultation') || undefined;
  departments.value = await request<Department[]>({ url: '/departments', method: 'GET' });
  selectedDepartmentId.value = aiConsultation.value?.recommendedDepartmentId || departments.value[0]?.id || '';
  if (!departments.value.some((item) => item.id === selectedDepartmentId.value)) {
    selectedDepartmentId.value = departments.value[0]?.id ?? '';
  }
  await loadDepartmentResources();
}

onMounted(initialize);
onShow(async () => {
  if (selectedDepartmentId.value) {
    await loadDepartmentResources();
  }
});
</script>

<style scoped>
.schedule {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.schedule-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16rpx;
}

.doctor {
  font-weight: 700;
  font-size: 32rpx;
}

.tag {
  align-self: flex-start;
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
  background: #ecfdf5;
  color: #047857;
  font-size: 24rpx;
}
</style>

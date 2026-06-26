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
      <picker :range="departments" range-key="name" :value="selectedDepartmentIndex" @change="onDepartmentChange($event)">
        <view class="input">{{ selectedDepartment?.name ?? '请选择科室' }}</view>
      </picker>
    </view>

    <view v-if="availableDates.length" class="card">
      <view class="title">选择日期</view>
      <picker :range="availableDates" :value="selectedDateIndex" @change="onDateChange($event)">
        <view class="input">{{ selectedDate || '请选择日期' }}</view>
      </picker>
    </view>

    <view v-if="selectedSchedule" class="card schedule-detail">
      <view class="schedule-head">
        <view>
          <view class="doctor">{{ selectedSchedule.doctorName }}</view>
          <view class="muted">{{ doctorInfo(selectedSchedule.doctorId) }}</view>
        </view>
        <button size="mini" @click="selectedSchedule = null">返回</button>
      </view>
      <view>{{ selectedSchedule.workDate }} · {{ selectedSchedule.period }}</view>
      <view class="time-list">
        <view v-for="slot in selectedSchedule.timeSlots" :key="slot.id" class="time-row">
          <view>
            <view class="time-value">{{ slot.startTime }}</view>
            <view class="muted">余号 {{ slot.available }} / {{ slot.capacity }}</view>
          </view>
          <button class="button time-button" :disabled="slot.available <= 0" @click="book(selectedSchedule, slot)">
            {{ slot.available > 0 ? '挂号并缴费' : '满号' }}
          </button>
        </view>
      </view>
    </view>

    <view v-for="schedule in filteredSchedules" v-else :key="schedule.id" class="card schedule">
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
      <view class="muted">{{ schedule.available > 0 ? '有号' : '满号' }} · 余号 {{ schedule.available }} / {{ schedule.capacity }}</view>
      <button class="button" :disabled="!schedule.timeSlots.length" @click="selectedSchedule = schedule">查看可约时间</button>
    </view>

    <view v-if="selectedDepartmentId && !filteredSchedules.length" class="card muted">
      当前日期暂无可预约医生排班
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
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
  timeSlots: TimeSlot[];
}

interface TimeSlot {
  id: string;
  startTime: string;
  capacity: number;
  booked: number;
  locked: number;
  available: number;
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
const selectedSchedule = ref<Schedule | null>(null);
const aiConsultation = ref<AiConsultation>();

const selectedDepartment = computed(() => departments.value.find((item) => item.id === selectedDepartmentId.value));
const recommendedDoctorIds = computed(() => aiConsultation.value?.recommendedDoctors.map((item) => item.doctorId) ?? []);
const BOOKABLE_DAY_SPAN = 7;

function formatDateKey(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function addDays(date: Date, days: number) {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
}

function isWithinBookingWindow(workDate: string) {
  if (!workDate) {
    return false;
  }
  const today = new Date();
  const startDate = formatDateKey(today);
  const endDate = formatDateKey(addDays(today, BOOKABLE_DAY_SPAN - 1));
  return workDate >= startDate && workDate <= endDate;
}

const bookableSchedules = computed(() =>
  schedules.value.filter((item) => isWithinBookingWindow(item.workDate))
);
const availableDates = computed(() => Array.from(new Set(bookableSchedules.value.map((item) => item.workDate))).sort());
const doctorMap = computed(() => new Map(doctors.value.map((item) => [item.id, item])));
const filteredSchedules = computed(() =>
  bookableSchedules.value
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
const selectedDepartmentIndex = computed(() => optionIndexById(departments.value, selectedDepartmentId.value));
const selectedDateIndex = computed(() => optionIndexByValue(availableDates.value, selectedDate.value));

function doctorInfo(doctorId: string) {
  const doctor = doctorMap.value.get(doctorId);
  if (!doctor) {
    return '医生信息加载中';
  }
  const extra = [doctor.title, doctor.specialty].filter(Boolean).join(' · ');
  return extra || '门诊医生';
}

function normalizeText(value: unknown) {
  if (typeof value === 'string') {
    return value;
  }
  if (typeof value === 'number') {
    return String(value);
  }
  if (value && typeof value === 'object') {
    const record = value as Record<string, unknown>;
    const year = record.year;
    const month = record.monthValue ?? record.month;
    const day = record.dayOfMonth ?? record.day;
    if (typeof year === 'number' && typeof month === 'number' && typeof day === 'number') {
      return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    }
    if (typeof record.name === 'string') {
      return record.name;
    }
  }
  return '';
}

function toDepartment(item: Record<string, unknown>): Department {
  return {
    id: normalizeText(item.id),
    name: normalizeText(item.name)
  };
}

function toDoctor(item: Record<string, unknown>): Doctor {
  return {
    id: normalizeText(item.id),
    name: normalizeText(item.name),
    title: normalizeText(item.title),
    departmentId: normalizeText(item.departmentId),
    specialty: normalizeText(item.specialty)
  };
}

function toSchedule(item: Record<string, unknown>): Schedule {
  return {
    id: normalizeText(item.id),
    doctorId: normalizeText(item.doctorId),
    doctorName: normalizeText(item.doctorName),
    departmentId: normalizeText(item.departmentId),
    workDate: normalizeText(item.workDate),
    period: normalizeText(item.period),
    capacity: Number(item.capacity ?? 0),
    booked: Number(item.booked ?? 0),
    locked: Number(item.locked ?? 0),
    available: Number(item.available ?? 0),
    status: normalizeText(item.status),
    timeSlots: Array.isArray(item.timeSlots)
      ? item.timeSlots.map((slot) => toTimeSlot(slot as Record<string, unknown>))
      : []
  };
}

function toTimeSlot(item: Record<string, unknown>): TimeSlot {
  return {
    id: normalizeText(item.id),
    startTime: normalizeText(item.startTime).slice(0, 5),
    capacity: Number(item.capacity ?? 0),
    booked: Number(item.booked ?? 0),
    locked: Number(item.locked ?? 0),
    available: Number(item.available ?? 0)
  };
}

function optionIndexById(options: Array<{ id: string }>, value: string) {
  const index = options.findIndex((item) => item.id === value);
  return index >= 0 ? index : 0;
}

function optionIndexByValue(options: string[], value: string) {
  const index = options.findIndex((item) => item === value);
  return index >= 0 ? index : 0;
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
    request<Record<string, unknown>[]>({ url: `/doctors?departmentId=${selectedDepartmentId.value}`, method: 'GET' }),
    request<Record<string, unknown>[]>({ url: `/schedules?departmentId=${selectedDepartmentId.value}`, method: 'GET' })
  ]);

  doctors.value = doctorList.map(toDoctor);
  schedules.value = scheduleList.map(toSchedule);
  syncSelectedDate();
}

async function onDepartmentChange(event: { detail: { value: string } }) {
  selectedSchedule.value = null;
  selectedDepartmentId.value = departments.value[Number(event.detail.value)]?.id ?? '';
  await loadDepartmentResources();
}

function onDateChange(event: { detail: { value: string } }) {
  selectedSchedule.value = null;
  selectedDate.value = availableDates.value[Number(event.detail.value)] ?? '';
}

async function book(schedule: Schedule, slot: TimeSlot) {
  let patient;
  try {
    patient = auth.requireBoundPatient();
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' });
    uni.navigateTo({ url: '/pages/real-name/index' });
    return;
  }

  try {
    const appointment = await request<Appointment>({
      url: '/appointments',
      method: 'POST',
      data: {
        scheduleId: slot.id,
        patientId: patient.id,
        patientName: patient.name,
        doctorId: schedule.doctorId,
        doctorName: schedule.doctorName,
        departmentId: schedule.departmentId,
        departmentName: selectedDepartment.value?.name,
        visitDate: schedule.workDate,
        period: schedule.period,
        startTime: slot.startTime,
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
        patientId: patient.id,
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
          patientId: patient.id,
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
  const departmentList = await request<Record<string, unknown>[]>({ url: '/departments', method: 'GET' });
  departments.value = departmentList.map(toDepartment);
  selectedDepartmentId.value = aiConsultation.value?.recommendedDepartmentId || departments.value[0]?.id || '';
  if (!departments.value.some((item) => item.id === selectedDepartmentId.value)) {
    selectedDepartmentId.value = departments.value[0]?.id ?? '';
  }
  await loadDepartmentResources();
}

onMounted(initialize);
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

.time-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  margin-top: 16rpx;
}

.time-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18rpx;
  padding: 18rpx 0;
  border-bottom: 1px solid #e2e8f0;
}

.time-row:last-child {
  border-bottom: none;
}

.time-value {
  color: #0f172a;
  font-size: 32rpx;
  font-weight: 700;
}

.time-button {
  width: 220rpx;
  margin: 0;
}
</style>

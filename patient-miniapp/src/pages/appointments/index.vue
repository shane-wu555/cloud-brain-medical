<template>
  <patient-nav-bar title="我的挂号" />
  <view class="page">
    <view v-for="item in visibleAppointments" :key="item.id" class="card appointment-card">
      <view class="row-between">
        <view class="appointment-main">
          <view class="title-sm">{{ item.departmentName || '门诊科室' }}</view>
          <view class="doctor-line">{{ item.doctorName || '待分配医生' }}</view>
        </view>
        <view :class="['status-tag', appointmentStatusClass(item)]">{{ appointmentStatusLabel(item) }}</view>
      </view>

      <view class="visit-line">{{ visitTimeText(item) }}</view>

      <view v-if="canCancel(item) || canRevisit(item)" class="action-row">
        <button v-if="canCancel(item)" class="cancel-button" @click="cancel(item)">{{ cancelLabel(item) }}</button>
        <button v-if="canRevisit(item)" class="revisit-button" @click="revisit(item)">复诊报到</button>
      </view>
    </view>

    <view v-if="!visibleAppointments.length" class="card muted">暂无挂号记录</view>
  </view>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { request } from '../../api/http';
import { useAuthStore } from '../../stores/auth';

interface Appointment {
  id: string;
  businessNo?: string;
  departmentName: string;
  doctorName: string;
  visitDate: string;
  period?: string;
  startTime?: string | number[] | { hour?: number; minute?: number; second?: number };
  status: string;
  paymentStatus?: string;
}

const auth = useAuthStore();
const appointments = ref<Appointment[]>([]);
const visibleAppointments = computed(() =>
  [...appointments.value].sort((a, b) => appointmentSortTime(b).localeCompare(appointmentSortTime(a)))
);

function todayStr() {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function appointmentStatusLabel(item: Appointment) {
  if (item.paymentStatus === 'REFUNDED') return '已退号';
  if (item.status === 'WAITING' || item.status === 'IN_VISIT') {
    return hasVisitStarted(item) ? '就诊中' : '待就诊';
  }
  return {
    PENDING_PAYMENT: '待缴费',
    CALLED: '已叫号',
    REVISIT_WAITING: '复诊等待',
    CANCELLED: '已取消',
    FINISHED: '已完成',
    EXPIRED: '已过期'
  }[item.status] ?? item.status;
}

function appointmentStatusClass(item: Appointment) {
  if (item.paymentStatus === 'REFUNDED') return 'cancelled';
  if (item.status === 'WAITING' || item.status === 'IN_VISIT') {
    return hasVisitStarted(item) ? 'progress' : 'queued';
  }
  return {
    PENDING_PAYMENT: 'pending',
    CALLED: 'progress',
    REVISIT_WAITING: 'queued',
    CANCELLED: 'cancelled',
    FINISHED: 'done',
    EXPIRED: 'cancelled'
  }[item.status] ?? 'muted-tag';
}

function appointmentDateTime(item: Appointment) {
  const time = normalizeStartTime(item.startTime) || '00:00';
  const parsed = new Date(`${item.visitDate}T${time}:00`);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

function hasVisitStarted(item: Appointment) {
  const start = appointmentDateTime(item);
  return !!start && start.getTime() <= Date.now();
}

function visitTimeText(item: Appointment) {
  const startTime = normalizeStartTime(item.startTime);
  return [item.visitDate, startTime || item.period].filter(Boolean).join(' ');
}

function appointmentSortTime(item: Appointment) {
  const startTime = normalizeStartTime(item.startTime) || '00:00';
  return `${item.visitDate}T${startTime}:00`;
}

function normalizeStartTime(value: Appointment['startTime']) {
  if (!value) {
    return '';
  }
  if (typeof value === 'string') {
    return value.slice(0, 5);
  }
  if (Array.isArray(value) && value.length >= 2) {
    return `${String(value[0]).padStart(2, '0')}:${String(value[1]).padStart(2, '0')}`;
  }
  const hour = value.hour;
  const minute = value.minute;
  if (typeof hour === 'number' && typeof minute === 'number') {
    return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
  }
  return '';
}

function canCancel(item: Appointment) {
  return item.visitDate > todayStr() && !['CANCELLED', 'FINISHED'].includes(item.status);
}

function canRevisit(item: Appointment) {
  return item.status === 'FINISHED' && item.visitDate === todayStr();
}

function cancelLabel(item: Appointment) {
  return item.paymentStatus === 'PAID' ? '取消并退费' : '取消';
}

onShow(async () => {
  await auth.loadProfile();
  let patient;
  try {
    patient = auth.requireBoundPatient();
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' });
    uni.navigateTo({ url: '/pages/real-name/index' });
    return;
  }
  appointments.value = await request<Appointment[]>({ url: `/appointments?patientId=${patient.id}`, method: 'GET' });
});

async function cancel(item: Appointment) {
  try {
    const patient = auth.requireBoundPatient();
    await request({ url: `/appointments/${item.id}/cancel`, method: 'POST' });
    uni.showToast({
      title: item.paymentStatus === 'PAID' ? '已取消，退款处理中' : '已取消',
      icon: 'none',
      duration: 2200
    });
    appointments.value = await request<Appointment[]>({ url: `/appointments?patientId=${patient.id}`, method: 'GET' });
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' });
  }
}

async function revisit(item: Appointment) {
  try {
    await request({ url: `/appointments/${item.id}/revisit`, method: 'POST' });
    uni.showToast({ title: '已加入复诊队列，请等候叫号', icon: 'none', duration: 2500 });
    const patient = auth.requireBoundPatient();
    appointments.value = await request<Appointment[]>({ url: `/appointments?patientId=${patient.id}`, method: 'GET' });
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' });
  }
}
</script>
<style scoped>
.appointment-card {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  border-left: 8rpx solid #2f80ed;
}

.row-between {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16rpx;
}

.title-sm {
  color: #172033;
  font-size: 34rpx;
  font-weight: 800;
}

.appointment-main {
  min-width: 0;
}

.doctor-line {
  margin-top: 8rpx;
  color: #334155;
  font-size: 28rpx;
}

.visit-line {
  padding: 18rpx;
  border-radius: 14rpx;
  background: #f8fafc;
  color: #0f766e;
  font-size: 28rpx;
  font-weight: 700;
}

.status-tag {
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  white-space: nowrap;
}

.pending {
  background: #fff7ed;
  color: #c2410c;
}

.queued {
  background: #eff6ff;
  color: #1d4ed8;
}

.progress {
  background: #ecfeff;
  color: #0f766e;
}

.done {
  background: #dcfce7;
  color: #166534;
}

.cancelled,
.muted-tag {
  background: #f1f5f9;
  color: #64748b;
}

.action-row {
  display: flex;
  gap: 16rpx;
  flex-wrap: wrap;
}

.cancel-button,
.revisit-button {
  align-self: flex-start;
  min-width: 160rpx;
  height: 64rpx;
  margin: 0;
  padding: 0 24rpx;
  border-radius: 10rpx;
  background: #eef6ff;
  color: #2f80ed;
  font-size: 26rpx;
  font-weight: 700;
  line-height: 64rpx;
}

.revisit-button {
  background: #ecfdf5;
  color: #0f766e;
}
</style>

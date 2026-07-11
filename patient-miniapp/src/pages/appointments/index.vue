<template>
  <patient-nav-bar title="我的挂号" />
  <view class="page">
    <view v-for="item in visibleAppointments" :key="item.id" class="card appointment-card">
      <view class="row-between">
        <view class="appointment-main">
          <view class="title-sm">{{ item.departmentName || '门诊科室' }}</view>
          <view class="doctor-line">{{ item.doctorName || '待分配医生' }}</view>
          <view v-if="item.roomName" class="room-line">{{ item.roomName }}</view>
        </view>
        <view :class="['status-tag', appointmentStatusClass(item)]">{{ appointmentStatusLabel(item) }}</view>
      </view>

      <view class="visit-line">{{ visitTimeText(item) }}</view>

      <view v-if="canCancel(item) || canRevisit(item)" class="action-row">
        <button v-if="canCancel(item)" class="cancel-button" @tap="openCancelDialog(item)">{{ cancelLabel(item) }}</button>
        <button v-if="canRevisit(item)" class="revisit-button" @tap="revisit(item)">复诊报到</button>
      </view>
    </view>

    <view v-if="!visibleAppointments.length" class="card muted">暂无挂号记录</view>

    <view v-if="pendingCancellation" class="confirm-mask">
      <view class="confirm-dialog">
        <view class="dialog-close" @tap="closeCancelDialog()">×</view>
        <view class="dialog-title">{{ cancelLabel(pendingCancellation) }}</view>
        <view class="dialog-body">
          <view class="confirm-row">
            <text class="confirm-label">就诊人：</text>
            <text class="confirm-value">{{ auth.boundPatient?.name || '当前就诊人' }}</text>
          </view>
          <view class="confirm-row">
            <text class="confirm-label">就诊科室：</text>
            <text>{{ pendingCancellation.departmentName || '门诊科室' }}</text>
          </view>
          <view class="confirm-row">
            <text class="confirm-label">医生：</text>
            <text>{{ pendingCancellation.doctorName || '待分配医生' }}</text>
          </view>
          <view class="confirm-row">
            <text class="confirm-label">就诊时间：</text>
            <text>{{ visitTimeText(pendingCancellation) }}</text>
          </view>
        </view>
        <view class="dialog-actions">
          <button class="dialog-secondary" @tap="closeCancelDialog()">先不取消</button>
          <button class="dialog-primary" @tap="confirmCancel()">
            {{ cancelSubmitting ? '处理中...' : cancelLabel(pendingCancellation) }}
          </button>
        </view>
      </view>
    </view>
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
  roomName?: string;
  visitDate: string;
  period?: string;
  startTime?: string | number[] | { hour?: number; minute?: number; second?: number };
  status: string;
  paymentStatus?: string;
}

const auth = useAuthStore();
const appointments = ref<Appointment[]>([]);
const pendingCancellation = ref<Appointment | null>(null);
const cancelSubmitting = ref(false);
const REVISIT_TIME_HINT = '当前非门诊时间，请于08:00-12:00或14:00-17:30内复诊签到';

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

function normalizeStartTime(value: Appointment['startTime']): string {
  if (!value) return '';
  if (typeof value === 'string') return value.slice(0, 5);
  if (Array.isArray(value)) {
    if (value.length < 2) return '';
    return `${String(value[0]).padStart(2, '0')}:${String(value[1]).padStart(2, '0')}`;
  }
  if (typeof value.hour === 'number' && typeof value.minute === 'number') {
    return `${String(value.hour).padStart(2, '0')}:${String(value.minute).padStart(2, '0')}`;
  }
  return '';
}

function canCancel(item: Appointment) {
  return item.visitDate > todayStr() && !['CANCELLED', 'FINISHED'].includes(item.status);
}

function canRevisit(item: Appointment) {
  return item.status === 'FINISHED' && item.visitDate === todayStr();
}

function isWithinOutpatientHours(date = new Date()) {
  const minutes = date.getHours() * 60 + date.getMinutes();
  const morningStart = 8 * 60;
  const morningEnd = 12 * 60;
  const afternoonStart = 14 * 60;
  const afternoonEnd = 17 * 60 + 30;
  return (minutes >= morningStart && minutes <= morningEnd)
    || (minutes >= afternoonStart && minutes <= afternoonEnd);
}

function cancelLabel(item: Appointment) {
  return item.paymentStatus === 'PAID' ? '取消并退费' : '取消';
}

function openCancelDialog(item: Appointment) {
  pendingCancellation.value = item;
}

function closeCancelDialog() {
  if (cancelSubmitting.value) {
    return;
  }
  pendingCancellation.value = null;
}

async function refreshAppointments() {
  const patient = auth.requireBoundPatient();
  appointments.value = await request<Appointment[]>({ url: `/appointments?patientId=${patient.id}`, method: 'GET' });
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

  await refreshAppointments();
});

async function confirmCancel() {
  const item = pendingCancellation.value;
  if (!item || cancelSubmitting.value) {
    return;
  }

  try {
    cancelSubmitting.value = true;
    uni.showLoading({ title: '处理中…', mask: true });
    await request({ url: `/appointments/${item.id}/cancel`, method: 'POST' });
    uni.hideLoading();
    pendingCancellation.value = null;
    uni.showToast({
      title: item.paymentStatus === 'PAID' ? '已取消，退款处理中' : '已取消',
      icon: 'none',
      duration: 2200
    });
    await refreshAppointments();
  } catch (error) {
    uni.hideLoading();
    uni.showToast({ title: (error as Error).message, icon: 'none' });
  } finally {
    cancelSubmitting.value = false;
  }
}

async function revisit(item: Appointment) {
  try {
    if (!isWithinOutpatientHours()) {
      uni.showToast({ title: REVISIT_TIME_HINT, icon: 'none', duration: 2500 });
      return;
    }
    await request({ url: `/appointments/${item.id}/revisit`, method: 'POST' });
    uni.showToast({ title: '已加入复诊队列，请等候叫号', icon: 'none', duration: 2500 });
    await refreshAppointments();
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
  border-left: 8rpx solid var(--patient-theme-strong);
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

.room-line {
  margin-top: 6rpx;
  color: #0f766e;
  font-size: 26rpx;
  font-weight: 700;
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
  background: var(--patient-theme-soft);
  color: var(--patient-theme-deep);
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

.confirm-mask {
  position: fixed;
  inset: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48rpx;
  background: rgba(0, 0, 0, 0.62);
}

.confirm-dialog {
  position: relative;
  width: 100%;
  overflow: hidden;
  border-radius: 30rpx;
  background: #fff;
}

.dialog-close {
  position: absolute;
  top: 26rpx;
  right: 30rpx;
  color: #111827;
  font-size: 54rpx;
  line-height: 1;
}

.dialog-title {
  padding: 64rpx 36rpx 28rpx;
  color: #1f2937;
  font-size: 42rpx;
  font-weight: 600;
  text-align: center;
}

.dialog-body {
  padding: 10rpx 46rpx 40rpx;
}

.confirm-row {
  display: flex;
  gap: 18rpx;
  padding: 18rpx 0;
  color: #1f2937;
  font-size: 31rpx;
  line-height: 1.45;
}

.confirm-label {
  flex-shrink: 0;
  color: #7b8494;
}

.confirm-value {
  color: var(--patient-theme-strong);
}

.confirm-note {
  margin-top: 14rpx;
  padding: 20rpx 22rpx;
  border-radius: 16rpx;
  background: #f8fafc;
  color: #475569;
  font-size: 26rpx;
  line-height: 1.7;
}

.dialog-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  border-top: 1px solid #edf2f7;
}

.dialog-secondary,
.dialog-primary {
  height: 96rpx;
  margin: 0;
  border-radius: 0;
  background: #fff;
  font-size: 32rpx;
  line-height: 96rpx;
}

.dialog-secondary {
  color: #1f2937;
  border-right: 1px solid #edf2f7;
}

.dialog-primary {
  color: #dc2626;
}

.cancel-button,
.revisit-button {
  align-self: flex-start;
  min-width: 160rpx;
  height: 64rpx;
  margin: 0;
  padding: 0 24rpx;
  border-radius: 10rpx;
  background: var(--patient-theme-soft);
  color: var(--patient-theme-strong);
  font-size: 26rpx;
  font-weight: 700;
  line-height: 64rpx;
}

.revisit-button {
  background: #ecfdf5;
  color: #0f766e;
}
</style>

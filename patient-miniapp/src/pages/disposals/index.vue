<template>
  <patient-nav-bar :title="pageTitle" />
  <view class="page">
    <view v-for="item in visibleDisposals" :key="item.id" class="card disposal-card">
      <view class="row-between">
        <view class="title-sm">{{ item.itemName }}</view>
        <view :class="['status-tag', statusClass(item.status, item.paymentStatus)]">
          {{ statusLabel(item.status, item.paymentStatus) }}
        </view>
      </view>

      <view class="muted">项目费用：¥{{ amountText(item.amount) }}</view>
      <view v-if="item.visitText" class="muted">计划就诊：{{ item.visitText }}</view>
      <view class="muted">执行科室：{{ item.roomName || '处置科' }}</view>
      <view v-if="item.roomName" class="muted">执行人员：{{ item.roomName }}</view>
      <view v-if="item.roomLocation" class="muted">执行地点：{{ item.roomLocation }}</view>
      <view v-if="item.purpose" class="section">
        <view class="label">处置目的</view>
        <view>{{ item.purpose }}</view>
      </view>
      <view v-if="item.resultSummary" class="section">
        <view class="label">处置结果</view>
        <view>{{ item.resultSummary }}</view>
      </view>
      <view v-if="item.completedAt" class="muted">完成时间：{{ formatDateTime(item.completedAt) }}</view>

      <button
        v-if="item.paymentStatus === 'UNPAID'"
        class="button compact-action"
        @click="goToPendingPayments()"
      >
        去待缴费页面处理
      </button>
    </view>

    <view v-if="!visibleDisposals.length" class="card muted">{{ emptyText }}</view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad, onShow } from '@dcloudio/uni-app';
import { request } from '../../api/http';
import { useAuthStore } from '../../stores/auth';
import { formatDateTime } from '../../utils/format';

interface Appointment {
  id: string;
  departmentName: string;
  doctorName: string;
  visitDate: string;
  period: string;
  startTime?: string | number[] | { hour?: number; minute?: number; second?: number };
}

interface MedicalOrder {
  id: string;
  appointmentId: string;
  itemName: string;
  purpose: string;
  amount: number;
  paymentStatus: 'UNPAID' | 'PAID' | 'FAILED';
  status: 'PENDING_PAYMENT' | 'WAITING_TRIAGE' | 'WAITING' | 'IN_PROGRESS' | 'COMPLETED' | 'MISSED';
  roomName: string;
  roomLocation: string;
  queueNumber: number | null;
  resultSummary: string;
  createdAt: string;
  completedAt: string;
}

const orders = ref<MedicalOrder[]>([]);
const appointments = ref<Appointment[]>([]);
const auth = useAuthStore();
const mode = ref<'arrangement' | 'record'>('arrangement');

const appointmentMap = computed(() => new Map(appointments.value.map((item) => [item.id, item])));
const disposals = computed(() =>
  orders.value
    .map((item) => {
      const appointment = appointmentMap.value.get(item.appointmentId);
      return {
        ...item,
        visitText: appointment ? `${appointment.visitDate} ${normalizeStartTime(appointment.startTime) || appointment.period} · ${appointment.departmentName}` : '',
        sortTime: item.completedAt || appointmentSortTime(appointment) || item.createdAt || ''
      };
    })
    .sort((a, b) => b.sortTime.localeCompare(a.sortTime))
);
const visibleDisposals = computed(() =>
  disposals.value.filter((item) =>
    mode.value === 'arrangement'
      ? item.paymentStatus === 'UNPAID' || !['COMPLETED', 'MISSED'].includes(item.status)
      : ['COMPLETED', 'MISSED'].includes(item.status)
  )
);
const pageTitle = computed(() => (mode.value === 'arrangement' ? '待处置安排' : '处置记录'));
const emptyText = computed(() => (mode.value === 'arrangement' ? '暂无待处置项目' : '暂无处置记录'));

onLoad((options) => {
  mode.value = options?.mode === 'record' ? 'record' : 'arrangement';
  uni.setNavigationBarTitle({ title: pageTitle.value });
});

function amountText(value: number) {
  return Number(value ?? 0).toFixed(2);
}

function appointmentSortTime(appointment?: Appointment) {
  if (!appointment) {
    return '';
  }
  const startTime = normalizeStartTime(appointment.startTime) || '00:00';
  return `${appointment.visitDate}T${startTime}:00`;
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

function statusLabel(status: MedicalOrder['status'], paymentStatus: MedicalOrder['paymentStatus']) {
  if (paymentStatus === 'UNPAID') {
    return '待缴费';
  }
  return {
    PENDING_PAYMENT: '待缴费',
    WAITING_TRIAGE: '待安排',
    WAITING: '待处置',
    IN_PROGRESS: '处置中',
    COMPLETED: '已完成',
    MISSED: '已顺延'
  }[status] ?? status;
}

function statusClass(status: MedicalOrder['status'], paymentStatus: MedicalOrder['paymentStatus']) {
  if (paymentStatus === 'UNPAID') {
    return 'pending';
  }
  return {
    PENDING_PAYMENT: 'pending',
    WAITING_TRIAGE: 'queued',
    WAITING: 'queued',
    IN_PROGRESS: 'progress',
    COMPLETED: 'done',
    MISSED: 'muted-tag'
  }[status] ?? 'muted-tag';
}

async function load() {
  await auth.loadProfile();
  let patient;
  try {
    patient = auth.requireBoundPatient();
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' });
    uni.navigateTo({ url: '/pages/real-name/index' });
    return;
  }
  const patientQuery = `patientId=${encodeURIComponent(patient.id)}`;
  const view = mode.value === 'arrangement' ? 'DISPOSAL_ARRANGEMENT' : 'DISPOSAL_RECORD';
  const [orderList, appointmentList] = await Promise.all([
    request<MedicalOrder[]>({ url: `/medical-orders?type=DISPOSAL&${patientQuery}&view=${view}`, method: 'GET' }),
    request<Appointment[]>({ url: `/appointments?${patientQuery}`, method: 'GET' })
  ]);
  orders.value = orderList;
  appointments.value = appointmentList;
}

function goToPendingPayments() {
  uni.navigateTo({ url: '/pages/pending-payments/index' });
}

onShow(load);
</script>

<style scoped>
.disposal-card {
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

.muted-tag {
  background: #f1f5f9;
  color: #64748b;
}

.compact-action {
  align-self: flex-start;
  width: auto;
  min-width: 0;
  height: 64rpx;
  margin: 10rpx 0 0;
  padding: 0 22rpx;
  border-radius: 10rpx;
  font-size: 26rpx;
  line-height: 64rpx;
}
</style>

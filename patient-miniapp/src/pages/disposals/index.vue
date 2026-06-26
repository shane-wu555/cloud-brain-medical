<template>
  <view class="page">
    <view class="card">
      <view class="title">处置安排</view>
      <view class="muted">查看输液、换药、注射、针灸等处置项目的缴费、排队和完成情况。</view>
    </view>

    <view v-for="item in disposals" :key="item.id" class="card disposal-card">
      <view class="row-between">
        <view class="title-sm">{{ item.projectName }}</view>
        <view :class="['status-tag', statusClass(item.status, item.paymentStatus)]">
          {{ statusLabel(item.status, item.paymentStatus) }}
        </view>
      </view>

      <view class="muted">项目费用：¥{{ amountText(item.amount) }}</view>
      <view v-if="item.visitText" class="muted">计划就诊：{{ item.visitText }}</view>
      <view v-if="item.executorName" class="muted">执行人员：{{ item.executorName }}</view>
      <view v-if="item.executionLocation" class="muted">执行地点：{{ item.executionLocation }}</view>
      <view v-if="item.purpose" class="section">
        <view class="label">处置目的</view>
        <view>{{ item.purpose }}</view>
      </view>
      <view v-if="item.resultSummary" class="section">
        <view class="label">处置结果</view>
        <view>{{ item.resultSummary }}</view>
      </view>
      <view v-if="item.completedAt" class="muted">完成时间：{{ item.completedAt }}</view>

      <button
        v-if="item.paymentStatus === 'UNPAID'"
        class="button"
        @click="goToPendingPayments()"
      >
        去待缴费项目处理
      </button>
    </view>

    <view v-if="!disposals.length" class="card muted">暂无处置项目</view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { request } from '../../api/http';

interface Appointment {
  id: string;
  departmentName: string;
  doctorName: string;
  visitDate: string;
  period: string;
}

interface MedicalOrder {
  id: string;
  appointmentId: string;
  projectName: string;
  purpose: string;
  amount: number;
  paymentStatus: 'UNPAID' | 'PAID' | 'FAILED';
  status: 'PENDING_PAYMENT' | 'WAITING_TRIAGE' | 'WAITING' | 'IN_PROGRESS' | 'COMPLETED' | 'MISSED';
  executorName: string;
  executionLocation: string;
  queueNumber: number | null;
  resultSummary: string;
  completedAt: string;
}

const orders = ref<MedicalOrder[]>([]);
const appointments = ref<Appointment[]>([]);

const appointmentMap = computed(() => new Map(appointments.value.map((item) => [item.id, item])));
const disposals = computed(() =>
  orders.value.map((item) => {
    const appointment = appointmentMap.value.get(item.appointmentId);
    return {
      ...item,
      visitText: appointment ? `${appointment.visitDate} ${appointment.period} · ${appointment.departmentName}` : ''
    };
  })
);

function amountText(value: number) {
  return Number(value ?? 0).toFixed(2);
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
  const [orderList, appointmentList] = await Promise.all([
    request<MedicalOrder[]>({ url: '/medical-orders?type=DISPOSAL', method: 'GET' }),
    request<Appointment[]>({ url: '/appointments', method: 'GET' })
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
</style>

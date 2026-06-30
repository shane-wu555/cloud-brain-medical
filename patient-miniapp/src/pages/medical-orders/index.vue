<template>
  <patient-nav-bar :title="pageTitle" />
  <view class="page">
    <view v-for="order in visibleOrders" :key="order.id" class="card row">
      <view class="row-between">
        <view class="title-sm">{{ orderTypeLabel(order.orderType) }}申请：{{ order.itemName }}</view>
        <view :class="['status-tag', statusClass(order.status, order.paymentStatus)]">
          {{ statusLabel(order.status, order.paymentStatus) }}
        </view>
      </view>
      <view><text class="label">项目：</text>{{ order.itemName }}</view>
      <view><text class="label">执行科室：</text>{{ executionDepartment(order) }}</view>
      <view v-if="order.roomLocation"><text class="label">执行地点：</text>{{ order.roomLocation }}</view>
      <view v-if="order.queueNumber != null && ['WAITING','IN_PROGRESS'].includes(order.status)">
        <text class="label">排队号：</text><text class="queue-num">第 {{ order.queueNumber }} 号</text>
      </view>
      <view v-if="order.bodyPart"><text class="label">检查部位：</text>{{ order.bodyPart }}</view>
      <view v-if="order.purpose"><text class="label">目的要求：</text>{{ order.purpose }}</view>
      <view class="muted">开立时间：{{ formatDateTime(order.createdAt) }}</view>
      <button
        v-if="order.paymentStatus === 'UNPAID'"
        class="button compact-action"
        @click="goToPendingPayments()"
      >
        去待缴费页面处理
      </button>
    </view>

    <view v-for="report in visibleReports" :key="report.id" class="card row">
      <view class="title-sm">{{ reportProjectTitle(report) }}</view>
      <view><text class="label">项目：</text>{{ reportProjectName(report) }}</view>
      <view><text class="label">所见/过程：</text>{{ report.findings || '—' }}</view>
      <view><text class="label">结论：</text>{{ report.conclusion }}</view>
      <view><text class="label">建议：</text>{{ report.advice || '—' }}</view>
      <view class="muted">{{ formatDateTime(report.confirmedAt) }} · 已由医生确认</view>
    </view>

    <view v-if="!visibleOrders.length && !visibleReports.length" class="card muted">{{ emptyText }}</view>
  </view>
</template>

<script setup lang="ts">
import { onLoad, onShow } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import { request } from '../../api/http';
import { useAuthStore } from '../../stores/auth';
import { formatDateTime } from '../../utils/format';

interface Report {
  id: string;
  medicalOrderId: string;
  reportType: 'CHECK' | 'LAB' | 'DISPOSAL';
  findings: string;
  conclusion: string;
  advice: string;
  confirmedAt: string;
}

interface MedicalOrder {
  id: string;
  orderType: 'CHECK' | 'LAB' | 'DISPOSAL';
  itemName: string;
  purpose: string;
  bodyPart: string;
  paymentStatus: 'UNPAID' | 'PAID' | 'FAILED';
  status: 'PENDING_PAYMENT' | 'WAITING_TRIAGE' | 'WAITING' | 'IN_PROGRESS' | 'COMPLETED' | 'MISSED';
  roomName: string;
  roomLocation: string;
  queueNumber?: number;
  createdAt: string;
}

const reports = ref<Report[]>([]);
const orders = ref<MedicalOrder[]>([]);
const auth = useAuthStore();
const mode = ref<'arrangement' | 'report'>('report');

const pendingOrders = computed(() =>
  orders.value
    .filter((item) => (item.orderType === 'CHECK' || item.orderType === 'LAB') && item.status !== 'COMPLETED')
    .sort((a, b) => orderSortTime(b).localeCompare(orderSortTime(a)))
);
const orderById = computed(() => new Map(orders.value.map((item) => [item.id, item])));
const visibleOrders = computed(() => (mode.value === 'arrangement' ? pendingOrders.value : []));
const visibleReports = computed(() =>
  mode.value === 'report'
    ? [...reports.value].sort((a, b) => reportSortTime(b).localeCompare(reportSortTime(a)))
    : []
);
const pageTitle = computed(() => (mode.value === 'arrangement' ? '检查检验安排' : '检查检验报告'));
const emptyText = computed(() => (mode.value === 'arrangement' ? '暂无待安排的检查检验项目' : '暂无已确认检查检验报告'));

onLoad((options) => {
  mode.value = options?.mode === 'arrangement' ? 'arrangement' : 'report';
  uni.setNavigationBarTitle({ title: pageTitle.value });
});

function fallbackDepartment(type: MedicalOrder['orderType']) {
  return type === 'LAB' ? '检验科' : '检查科';
}

function orderTypeLabel(type: MedicalOrder['orderType']) {
  return {
    CHECK: '检查',
    LAB: '检验',
    DISPOSAL: '处置'
  }[type];
}

function reportProjectName(report: Report) {
  return orderById.value.get(report.medicalOrderId)?.itemName || `${orderTypeLabel(report.reportType)}项目`;
}

function reportProjectTitle(report: Report) {
  return `${orderTypeLabel(report.reportType)}报告：${reportProjectName(report)}`;
}

function orderSortTime(order: MedicalOrder) {
  return order.createdAt || '';
}

function reportSortTime(report: Report) {
  return report.confirmedAt || '';
}

function executionDepartment(order: MedicalOrder) {
  return order.roomName || fallbackDepartment(order.orderType);
}

function statusLabel(status: MedicalOrder['status'], paymentStatus: MedicalOrder['paymentStatus']) {
  if (paymentStatus === 'UNPAID') {
    return '待缴费';
  }
  return {
    PENDING_PAYMENT: '待缴费',
    WAITING_TRIAGE: '待安排',
    WAITING: '待执行',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成',
    MISSED: '已过号'
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

function goToPendingPayments() {
  uni.navigateTo({ url: '/pages/pending-payments/index' });
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
  const patientQuery = `patientId=${encodeURIComponent(patient.id)}`;
  const [orderResponse, response] = await Promise.all([
    request<MedicalOrder[]>({ url: `/medical-orders?${patientQuery}`, method: 'GET' }),
    request<Report[]>({ url: `/medical-orders/reports?${patientQuery}`, method: 'GET' })
  ]);
  orders.value = orderResponse;
  reports.value = response.filter((item) => item.reportType === 'CHECK' || item.reportType === 'LAB');
});
</script>

<style scoped>
.row {
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
  color: #0f766e;
  font-weight: 600;
}

.queue-num {
  color: #1d4ed8;
  font-weight: 700;
  font-size: 34rpx;
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

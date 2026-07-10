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

      <view class="field-row">
        <text class="label">执行科室：</text>
        <text class="field-value">{{ executionDepartment(order) }}</text>
      </view>
      <view v-if="order.roomLocation" class="field-row">
        <text class="label">执行地点：</text>
        <text class="field-value">{{ order.roomLocation }}</text>
      </view>
      <view v-if="order.queueNumber != null && ['WAITING', 'IN_PROGRESS'].includes(order.status)" class="field-row">
        <text class="label">排队叫号：</text>
        <text class="field-value queue-num">第 {{ order.queueNumber }} 号</text>
      </view>
      <view v-if="order.bodyPart" class="field-row">
        <text class="label">检查部位：</text>
        <text class="field-value">{{ order.bodyPart }}</text>
      </view>
      <view class="muted">开立时间：{{ formatDateTime(order.createdAt) }}</view>

      <button
        v-if="order.paymentStatus === 'UNPAID'"
        class="button compact-action"
        @click="goToPendingPayments()"
      >
        去待缴费页面处理
      </button>
    </view>

    <view v-for="report in visibleReports" :key="report.id" class="card report-card">
      <view class="row-between report-header">
        <view class="report-main">
          <view class="item-title">{{ reportProjectName(report) }}</view>
          <view class="item-desc">{{ orderTypeLabel(report.reportType) }}报告</view>
        </view>
        <view class="status-tag done">已确认</view>
      </view>

      <view class="muted">确认时间：{{ formatDateTime(report.confirmedAt) }}</view>

      <view class="section">
        <view class="label">所见过程</view>
        <view v-if="formatReportLines(report.findings).length" class="section-lines">
          <view
            v-for="(line, index) in formatReportLines(report.findings)"
            :key="`findings-${report.id}-${index}`"
            class="section-line"
          >
            {{ line }}
          </view>
        </view>
        <view v-else class="section-content">暂无</view>
      </view>

      <view class="section">
        <view class="label">结论</view>
        <view v-if="formatReportLines(report.conclusion).length" class="section-lines">
          <view
            v-for="(line, index) in formatReportLines(report.conclusion)"
            :key="`conclusion-${report.id}-${index}`"
            class="section-line"
          >
            {{ line }}
          </view>
        </view>
        <view v-else class="section-content">暂无</view>
      </view>

      <view v-if="report.advice" class="section">
        <view class="label">建议</view>
        <view v-if="formatReportLines(report.advice).length" class="section-lines">
          <view
            v-for="(line, index) in formatReportLines(report.advice)"
            :key="`advice-${report.id}-${index}`"
            class="section-line"
          >
            {{ line }}
          </view>
        </view>
        <view v-else class="section-content">{{ report.advice }}</view>
      </view>
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

function orderSortTime(order: MedicalOrder) {
  return order.createdAt || '';
}

function reportSortTime(report: Report) {
  return report.confirmedAt || '';
}

function formatReportLines(content?: string) {
  if (!content) {
    return [];
  }

  const barcodePattern = /(?:\u6761\u5f62\u53f7|\u6761\u7801\u53f7|barcode)\s*[:：]?\s*[A-Za-z0-9-]+/giu;
  const abnormalLabelPattern = /\u5f02\u5e38\u6307\u6807\s*[:：]?\s*/u;

  const normalized = content
    .replace(barcodePattern, '')
    .replace(/\r\n/g, '\n')
    .replace(/\n+/g, '\n')
    .trim();

  const lines = normalized
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean);

  const result: string[] = [];

  for (const rawLine of lines) {
    const line = rawLine
      .replace(/^[-*•·●▪■◆◇○]+\s*/u, '')
      .replace(/^\d+[.)、]\s*/u, '')
      .trim();

    if (!line) {
      continue;
    }

    if (abnormalLabelPattern.test(line)) {
      const parts = line.split(abnormalLabelPattern);
      const before = parts[0]?.trim();
      const abnormalContent = parts.slice(1).join(' ').trim();

      if (before) {
        result.push(before.replace(/[：:，,；;、]+$/u, '').trim());
      }

      abnormalContent
        .split(/[；;，,、]+/u)
        .map((item) => item.trim())
        .map((item) => item.replace(/^[-*•·●▪■◆◇○]+\s*/u, ''))
        .map((item) => item.replace(/^\d+[.)、]\s*/u, ''))
        .filter(Boolean)
        .forEach((item) => result.push(item));

      continue;
    }

    result.push(line);
  }

  return result.filter(Boolean);
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
  const [orderResponse, reportResponse] = await Promise.all([
    request<MedicalOrder[]>({ url: `/medical-orders?${patientQuery}`, method: 'GET' }),
    request<Report[]>({ url: `/medical-orders/reports?${patientQuery}`, method: 'GET' })
  ]);

  orders.value = orderResponse;
  reports.value = reportResponse.filter((item) => item.reportType === 'CHECK' || item.reportType === 'LAB');
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
  color: #0f172a;
  font-size: 32rpx;
  font-weight: 700;
  line-height: 1.4;
}

.item-title {
  color: #0f172a;
  font-size: 30rpx;
  font-weight: 700;
  line-height: 1.4;
}

.item-desc {
  margin-top: 8rpx;
  color: #64748b;
  font-size: 26rpx;
  line-height: 1.5;
}

.label {
  color: #0f766e;
  font-size: 27rpx;
  font-weight: 600;
}

.field-row {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  line-height: 1.6;
}

.field-row .label {
  margin-bottom: 0;
}

.field-value {
  color: #0f172a;
  font-size: 27rpx;
  line-height: 1.6;
  word-break: break-word;
}

.queue-num {
  color: var(--patient-theme-strong);
  font-weight: 700;
  font-size: 27rpx;
}

.status-tag {
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 700;
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

.report-card {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.report-header {
  margin-bottom: 2rpx;
}

.report-main {
  min-width: 0;
  flex: 1;
}

.section {
  line-height: 1.7;
}

.section-content,
.section-line {
  color: #334155;
  font-size: 27rpx;
  line-height: 1.7;
  word-break: break-word;
}

.section-lines {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}
</style>

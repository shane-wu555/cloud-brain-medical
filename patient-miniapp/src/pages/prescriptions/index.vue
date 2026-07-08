<template>
  <patient-nav-bar :title="pageTitle" />
  <view class="page">
    <view v-for="prescription in visiblePrescriptions" :key="prescription.id" class="card prescription-card">
      <view class="row-between">
        <view class="title-sm">{{ prescriptionTitle(prescription) }}</view>
        <view :class="['status-tag', statusClass(prescription.status)]">{{ statusLabel(prescription.status) }}</view>
      </view>
      <view class="muted">诊断：{{ prescription.diagnosis || '暂无' }}</view>
      <view class="muted">总金额：¥{{ amountText(prescription.totalAmount) }}</view>
      <view v-if="prescription.confirmedAt" class="muted">开方时间：{{ formatDateTime(prescription.confirmedAt) }}</view>
      <view v-if="prescription.paidAt" class="muted">缴费时间：{{ formatDateTime(prescription.paidAt) }}</view>
      <view v-if="prescription.dispensedAt" class="muted">发药时间：{{ formatDateTime(prescription.dispensedAt) }}</view>
      <view v-if="prescription.returnedAt" class="muted">退药时间：{{ formatDateTime(prescription.returnedAt) }}</view>
      <view v-if="prescription.returnReason" class="muted">退药原因：{{ prescription.returnReason }}</view>

      <view class="section">
        <view class="label">药品明细</view>
        <view
          v-for="item in prescription.items"
          :key="item.id"
          class="item-row"
        >
          <view>{{ item.drugName }} × {{ item.quantity }}</view>
          <view class="muted">{{ item.dosage }} / {{ item.frequency }}</view>
        </view>
      </view>

      <button
        v-if="canPay(prescription)"
        class="button compact-action"
        @click="goToPendingPayments()"
      >
        去待缴费页面处理
      </button>
    </view>

    <view v-if="!visiblePrescriptions.length" class="card muted">{{ emptyText }}</view>
  </view>
</template>

<script setup lang="ts">
import { onLoad, onShow } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import { request } from '../../api/http';
import { useAuthStore } from '../../stores/auth';
import { formatDateTime } from '../../utils/format';

interface PrescriptionItem {
  id: string;
  prescriptionId: string;
  drugId: string;
  drugName: string;
  quantity: number;
  dosage: string;
  usage: string;
  frequency: string;
  days: number;
  note: string;
  unitPrice: number;
  amount: number;
}

interface Prescription {
  id: string;
  prescriptionNo: string;
  appointmentId: string;
  medicalRecordId: string;
  patientId: string;
  patientName: string;
  doctorId: string;
  diagnosis: string;
  status: 'DRAFT' | 'CONFIRMED' | 'PENDING_PAYMENT' | 'PAID' | 'WAITING_DISPENSE' | 'DISPENSED' | 'RETURNED' | 'RETURN_PENDING_REFUND' | 'RETURN_REFUNDED' | 'CANCELLED';
  totalAmount: number;
  paymentOrderId: string;
  createdAt: string;
  confirmedAt: string;
  paidAt: string;
  dispensedAt: string;
  returnedAt: string;
  dispensedBy: string;
  returnedBy: string;
  returnReason: string;
  items: PrescriptionItem[];
}

const auth = useAuthStore();
const prescriptions = ref<Prescription[]>([]);
const mode = ref<'arrangement' | 'record'>('arrangement');
const visiblePrescriptions = computed(() =>
  prescriptions.value
    .filter((item) =>
      mode.value === 'arrangement'
        ? ['CONFIRMED', 'PENDING_PAYMENT', 'PAID', 'WAITING_DISPENSE'].includes(item.status)
        : ['DISPENSED', 'RETURNED', 'RETURN_PENDING_REFUND', 'RETURN_REFUNDED', 'CANCELLED'].includes(item.status)
    )
    .sort((a, b) => prescriptionSortTime(b).localeCompare(prescriptionSortTime(a)))
);
const pageTitle = computed(() => (mode.value === 'arrangement' ? '待取药安排' : '取药退药记录'));
const emptyText = computed(() => (mode.value === 'arrangement' ? '暂无待取药安排' : '暂无取药记录'));

onLoad((options) => {
  mode.value = options?.mode === 'record' ? 'record' : 'arrangement';
  uni.setNavigationBarTitle({ title: pageTitle.value });
});

function amountText(value: number) {
  return Number(value ?? 0).toFixed(2);
}

function prescriptionSortTime(value: Prescription) {
  return value.returnedAt || value.dispensedAt || value.paidAt || value.confirmedAt || value.createdAt || '';
}

function prescriptionTitle(prescription: Prescription) {
  return prescription.diagnosis ? `${prescription.diagnosis}药方` : '药方';
}

function statusLabel(status: Prescription['status']) {
  return {
    DRAFT: '草稿',
    CONFIRMED: '已确认',
    PENDING_PAYMENT: '待缴费',
    PAID: '已缴费',
    WAITING_DISPENSE: '待发药',
    DISPENSED: '已发药',
    RETURNED: '未缴费（已退药）',
    RETURN_PENDING_REFUND: '未退费（已退药）',
    RETURN_REFUNDED: '已退费（已退药）',
    CANCELLED: '已取消'
  }[status] ?? status;
}

function statusClass(status: Prescription['status']) {
  return {
    DRAFT: 'draft',
    CONFIRMED: 'confirmed',
    PENDING_PAYMENT: 'pending',
    PAID: 'paid',
    WAITING_DISPENSE: 'waiting',
    DISPENSED: 'done',
    RETURNED: 'returned',
    RETURN_PENDING_REFUND: 'returned',
    RETURN_REFUNDED: 'returned',
    CANCELLED: 'cancelled'
  }[status] ?? 'draft';
}

function canPay(prescription: Prescription) {
  return prescription.status === 'PENDING_PAYMENT' || prescription.status === 'CONFIRMED';
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
  const view = mode.value === 'arrangement' ? 'DISPENSE_ARRANGEMENT' : 'DISPENSE_RECORD';
  prescriptions.value = await request<Prescription[]>({
    url: `/prescriptions?patientId=${encodeURIComponent(patient.id)}&view=${view}`,
    method: 'GET'
  });
}

function goToPendingPayments() {
  uni.navigateTo({ url: '/pages/pending-payments/index' });
}

onShow(load);
</script>

<style scoped>
.prescription-card {
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
  margin-bottom: 8rpx;
  color: #0f766e;
  font-size: 24rpx;
  font-weight: 600;
}

.section {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.item-row {
  padding: 16rpx;
  border-radius: 12rpx;
  background: #f8fafc;
}

.status-tag {
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  white-space: nowrap;
}

.draft {
  background: #f8fafc;
  color: #475569;
}

.confirmed {
  background: var(--patient-theme-soft);
  color: var(--patient-theme-deep);
}

.pending {
  background: #fff7ed;
  color: #c2410c;
}

.paid {
  background: #ecfeff;
  color: #0f766e;
}

.waiting {
  background: #ecfccb;
  color: #3f6212;
}

.done {
  background: #dcfce7;
  color: #166534;
}

.returned {
  background: var(--patient-theme-soft);
  color: var(--patient-theme-deep);
}

.cancelled {
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

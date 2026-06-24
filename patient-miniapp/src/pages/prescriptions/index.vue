<template>
  <view class="page">
    <view class="card">
      <view class="title">药方</view>
      <view class="muted">在这里统一查看处方内容、药方状态、缴费情况和发药进度。</view>
    </view>

    <view v-for="prescription in prescriptions" :key="prescription.id" class="card prescription-card">
      <view class="row-between">
        <view class="title-sm">{{ prescription.prescriptionNo }}</view>
        <view :class="['status-tag', statusClass(prescription.status)]">{{ statusLabel(prescription.status) }}</view>
      </view>
      <view class="muted">诊断：{{ prescription.diagnosis || '暂无' }}</view>
      <view class="muted">总金额：¥{{ amountText(prescription.totalAmount) }}</view>
      <view v-if="prescription.confirmedAt" class="muted">开方时间：{{ prescription.confirmedAt }}</view>
      <view v-if="prescription.paidAt" class="muted">缴费时间：{{ prescription.paidAt }}</view>
      <view v-if="prescription.dispensedAt" class="muted">发药时间：{{ prescription.dispensedAt }}</view>
      <view v-if="prescription.returnedAt" class="muted">退药时间：{{ prescription.returnedAt }}</view>
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
        class="button"
        @click="pay(prescription)"
      >
        处方缴费 ¥{{ amountText(prescription.totalAmount) }}
      </button>
    </view>

    <view v-if="!prescriptions.length" class="card muted">暂无药方记录</view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app';
import { ref } from 'vue';
import { request } from '../../api/http';
import { useAuthStore } from '../../stores/auth';

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
  status: 'DRAFT' | 'CONFIRMED' | 'PENDING_PAYMENT' | 'PAID' | 'WAITING_DISPENSE' | 'DISPENSED' | 'RETURNED' | 'CANCELLED';
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

function amountText(value: number) {
  return Number(value ?? 0).toFixed(2);
}

function statusLabel(status: Prescription['status']) {
  return {
    DRAFT: '草稿',
    CONFIRMED: '已确认',
    PENDING_PAYMENT: '待缴费',
    PAID: '已缴费',
    WAITING_DISPENSE: '待发药',
    DISPENSED: '已发药',
    RETURNED: '已退药',
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
    CANCELLED: 'cancelled'
  }[status] ?? 'draft';
}

function canPay(prescription: Prescription) {
  return prescription.status === 'PENDING_PAYMENT' || prescription.status === 'CONFIRMED';
}

async function load() {
  prescriptions.value = await request<Prescription[]>({ url: '/prescriptions', method: 'GET' });
}

async function pay(prescription: Prescription) {
  if (!auth.user) {
    return;
  }
  try {
    await request({
      url: '/payments/orders',
      method: 'POST',
      data: {
        businessType: 'PRESCRIPTION',
        businessId: prescription.id,
        patientId: auth.user.id,
        amount: prescription.totalAmount,
        paymentMethod: 'WECHAT_TEST'
      }
    });
    await request({
      url: '/payments/test-callback',
      method: 'POST',
      data: {
        businessType: 'PRESCRIPTION',
        businessId: prescription.id,
        patientId: auth.user.id,
        channel: 'WECHAT',
        channelTradeNo: `wx-rx-${prescription.id}-${Date.now()}`
      }
    });
    uni.showToast({ title: '处方缴费成功', icon: 'success' });
    await load();
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' });
  }
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
  background: #eff6ff;
  color: #1d4ed8;
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
  background: #fee2e2;
  color: #b91c1c;
}

.cancelled {
  background: #f1f5f9;
  color: #64748b;
}
</style>

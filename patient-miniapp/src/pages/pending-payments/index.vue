<template>
  <view class="page">
    <view class="card">
      <view class="title">待缴费项目</view>
      <view class="muted">集中查看挂号费、检查费、检验费、处置费和药费，按费用类别分开展示。</view>
    </view>

    <view v-if="loadWarning" class="card warning-card">
      <view class="warning-title">部分数据加载失败</view>
      <view class="muted">{{ loadWarning }}</view>
    </view>

    <view class="summary-grid">
      <view class="card summary-card">
        <view class="summary-label">待缴总金额</view>
        <view class="summary-value">¥{{ amountText(totalAmount) }}</view>
      </view>
      <view class="card summary-card">
        <view class="summary-label">待缴项目数</view>
        <view class="summary-value">{{ totalCount }}</view>
      </view>
    </view>

    <view class="card">
      <view class="section-head">
        <view class="section-title">费用概览</view>
        <view class="muted">先看总览，再逐项缴费</view>
      </view>
      <view class="category-grid">
        <view v-for="item in categorySummaries" :key="item.key" class="category-card">
          <view class="category-name">{{ item.label }}</view>
          <view class="category-amount">¥{{ amountText(item.amount) }}</view>
          <view class="muted">{{ item.count }} 项</view>
        </view>
      </view>
    </view>

    <view v-if="registrationItems.length" class="card">
      <view class="section-head">
        <view class="section-title">挂号费</view>
        <view class="muted">{{ registrationItems.length }} 项</view>
      </view>
      <view v-for="item in registrationItems" :key="item.businessId" class="item-row">
        <view class="item-main">
          <view class="item-title">{{ item.title }}</view>
          <view class="item-desc">{{ item.description }}</view>
          <view v-if="item.note" class="muted">{{ item.note }}</view>
        </view>
        <view class="item-actions">
          <view class="amount">¥{{ amountText(item.amount) }}</view>
          <button class="button mini-pay" @click="pay(item)">去缴费</button>
        </view>
      </view>
    </view>

    <view v-if="checkItems.length" class="card">
      <view class="section-head">
        <view class="section-title">检查费</view>
        <view class="muted">{{ checkItems.length }} 项</view>
      </view>
      <view v-for="item in checkItems" :key="item.businessId" class="item-row">
        <view class="item-main">
          <view class="item-title">{{ item.title }}</view>
          <view class="item-desc">{{ item.description }}</view>
          <view v-if="item.note" class="muted">{{ item.note }}</view>
        </view>
        <view class="item-actions">
          <view class="amount">¥{{ amountText(item.amount) }}</view>
          <button class="button mini-pay" @click="pay(item)">去缴费</button>
        </view>
      </view>
    </view>

    <view v-if="labItems.length" class="card">
      <view class="section-head">
        <view class="section-title">检验费</view>
        <view class="muted">{{ labItems.length }} 项</view>
      </view>
      <view v-for="item in labItems" :key="item.businessId" class="item-row">
        <view class="item-main">
          <view class="item-title">{{ item.title }}</view>
          <view class="item-desc">{{ item.description }}</view>
          <view v-if="item.note" class="muted">{{ item.note }}</view>
        </view>
        <view class="item-actions">
          <view class="amount">¥{{ amountText(item.amount) }}</view>
          <button class="button mini-pay" @click="pay(item)">去缴费</button>
        </view>
      </view>
    </view>

    <view v-if="disposalItems.length" class="card">
      <view class="section-head">
        <view class="section-title">处置费</view>
        <view class="muted">{{ disposalItems.length }} 项</view>
      </view>
      <view v-for="item in disposalItems" :key="item.businessId" class="item-row">
        <view class="item-main">
          <view class="item-title">{{ item.title }}</view>
          <view class="item-desc">{{ item.description }}</view>
          <view v-if="item.note" class="muted">{{ item.note }}</view>
        </view>
        <view class="item-actions">
          <view class="amount">¥{{ amountText(item.amount) }}</view>
          <button class="button mini-pay" @click="pay(item)">去缴费</button>
        </view>
      </view>
    </view>

    <view v-if="prescriptionItems.length" class="card">
      <view class="section-head">
        <view class="section-title">药费</view>
        <view class="muted">{{ prescriptionItems.length }} 项</view>
      </view>
      <view v-for="item in prescriptionItems" :key="item.businessId" class="item-row">
        <view class="item-main">
          <view class="item-title">{{ item.title }}</view>
          <view class="item-desc">{{ item.description }}</view>
          <view v-if="item.note" class="muted">{{ item.note }}</view>
        </view>
        <view class="item-actions">
          <view class="amount">¥{{ amountText(item.amount) }}</view>
          <button class="button mini-pay" @click="pay(item)">去缴费</button>
        </view>
      </view>
    </view>

    <view v-if="!totalCount" class="card muted">暂无待缴费项目</view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { request } from '../../api/http';
import { useAuthStore } from '../../stores/auth';

interface Appointment {
  id: string;
  departmentName: string;
  doctorName: string;
  visitDate: string;
  period: string;
  status: string;
  paymentStatus: string;
}

interface PaymentOrder {
  id: string;
  businessType: string;
  businessId: string;
  amount: number;
  status: string;
}

interface MedicalOrder {
  id: string;
  orderType: 'CHECK' | 'LAB' | 'DISPOSAL';
  projectName: string;
  amount: number;
  paymentStatus: string;
  status: string;
  urgency: string;
}

interface Prescription {
  id: string;
  prescriptionNo: string;
  diagnosis: string;
  totalAmount: number;
  status: 'DRAFT' | 'CONFIRMED' | 'PENDING_PAYMENT' | 'PAID' | 'WAITING_DISPENSE' | 'DISPENSED' | 'RETURNED' | 'CANCELLED';
}

interface PendingItem {
  businessType: 'APPOINTMENT' | 'MEDICAL_ORDER' | 'PRESCRIPTION';
  businessId: string;
  feeType: 'REGISTRATION' | 'CHECK' | 'LAB' | 'DISPOSAL' | 'DRUG';
  title: string;
  description: string;
  note?: string;
  amount: number;
}

const auth = useAuthStore();
const registrationItems = ref<PendingItem[]>([]);
const medicalOrderItems = ref<PendingItem[]>([]);
const prescriptionItems = ref<PendingItem[]>([]);
const loadWarning = ref('');

const checkItems = computed(() => medicalOrderItems.value.filter((item) => item.feeType === 'CHECK'));
const labItems = computed(() => medicalOrderItems.value.filter((item) => item.feeType === 'LAB'));
const disposalItems = computed(() => medicalOrderItems.value.filter((item) => item.feeType === 'DISPOSAL'));

const totalAmount = computed(() =>
  [...registrationItems.value, ...medicalOrderItems.value, ...prescriptionItems.value]
    .reduce((sum, item) => sum + Number(item.amount ?? 0), 0)
);
const totalCount = computed(() => registrationItems.value.length + medicalOrderItems.value.length + prescriptionItems.value.length);

const categorySummaries = computed(() => [
  summarizeCategory('registration', '挂号费', registrationItems.value),
  summarizeCategory('check', '检查费', checkItems.value),
  summarizeCategory('lab', '检验费', labItems.value),
  summarizeCategory('disposal', '处置费', disposalItems.value),
  summarizeCategory('drug', '药费', prescriptionItems.value)
]);

function summarizeCategory(key: string, label: string, items: PendingItem[]) {
  return {
    key,
    label,
    count: items.length,
    amount: items.reduce((sum, item) => sum + Number(item.amount ?? 0), 0)
  };
}

function amountText(value: number) {
  return Number(value ?? 0).toFixed(2);
}

function orderTypeLabel(type: MedicalOrder['orderType']) {
  return {
    CHECK: '检查费',
    LAB: '检验费',
    DISPOSAL: '处置费'
  }[type] ?? '医疗技术项目';
}

function urgencyLabel(value: string) {
  return value === 'EMERGENCY' ? '急诊' : '普通';
}

function prescriptionStatusLabel(status: Prescription['status']) {
  return {
    DRAFT: '草稿',
    CONFIRMED: '已开立，待缴费',
    PENDING_PAYMENT: '待缴费',
    PAID: '已缴费',
    WAITING_DISPENSE: '待发药',
    DISPENSED: '已发药',
    RETURNED: '已退药',
    CANCELLED: '已取消'
  }[status] ?? status;
}

async function load() {
  loadWarning.value = '';
  const warnings: string[] = [];
  const [appointmentsResult, paymentsResult, medicalOrdersResult, prescriptionsResult] = await Promise.allSettled([
    request<Appointment[]>({ url: '/appointments?status=PENDING_PAYMENT', method: 'GET' }),
    request<PaymentOrder[]>({ url: '/payments?businessType=APPOINTMENT&status=PENDING', method: 'GET' }),
    request<MedicalOrder[]>({ url: '/medical-orders?status=PENDING_PAYMENT', method: 'GET' }),
    request<Prescription[]>({ url: '/prescriptions', method: 'GET' })
  ]);

  const appointments = unwrapResult(appointmentsResult, [], '挂号费');
  const payments = unwrapResult(paymentsResult, [], '挂号费支付单');
  const medicalOrders = unwrapResult(medicalOrdersResult, [], '检查检验处置费');
  const prescriptions = unwrapResult(prescriptionsResult, [], '药费');

  if (warnings.length) {
    loadWarning.value = `当前未成功加载：${warnings.join('、')}。可以先查看已加载出来的项目。`;
  }

  const paymentByBusinessId = new Map(payments.map((item) => [item.businessId, item]));

  registrationItems.value = appointments
    .filter((item) => item.paymentStatus === 'UNPAID' || item.paymentStatus === 'FAILED')
    .map((item) => ({
      businessType: 'APPOINTMENT' as const,
      businessId: item.id,
      feeType: 'REGISTRATION' as const,
      title: `${item.departmentName} · ${item.doctorName}`,
      description: `${item.visitDate} ${item.period}`,
      note: `当前状态：${item.status}，挂号后需完成缴费才能正常就诊`,
      amount: paymentByBusinessId.get(item.id)?.amount ?? 0.01
    }));

  medicalOrderItems.value = medicalOrders
    .filter((item) => item.paymentStatus === 'UNPAID')
    .map((item) => ({
      businessType: 'MEDICAL_ORDER' as const,
      businessId: item.id,
      feeType: item.orderType,
      title: item.projectName,
      description: `${orderTypeLabel(item.orderType)} · ${urgencyLabel(item.urgency)}`,
      note: `当前状态：${item.status}`,
      amount: item.amount
    }));

  prescriptionItems.value = prescriptions
    .filter((item) => item.status === 'PENDING_PAYMENT' || item.status === 'CONFIRMED')
    .map((item) => ({
      businessType: 'PRESCRIPTION' as const,
      businessId: item.id,
      feeType: 'DRUG' as const,
      title: `处方 ${item.prescriptionNo}`,
      description: item.diagnosis || '待医生确认诊断',
      note: `当前状态：${prescriptionStatusLabel(item.status)}`,
      amount: item.totalAmount
    }));

  function unwrapResult<T>(result: PromiseSettledResult<T>, fallback: T, label: string): T {
    if (result.status === 'fulfilled') {
      return result.value;
    }
    warnings.push(label);
    console.error(`pending-payments load failed: ${label}`, result.reason);
    return fallback;
  }
}

async function pay(item: PendingItem) {
  if (!auth.user) {
    return;
  }
  try {
    await request({
      url: '/payments/orders',
      method: 'POST',
      data: {
        businessType: item.businessType,
        businessId: item.businessId,
        patientId: auth.user.id,
        amount: item.amount,
        paymentMethod: 'WECHAT_TEST'
      }
    });
    await request({
      url: '/payments/test-callback',
      method: 'POST',
      data: {
        businessType: item.businessType,
        businessId: item.businessId,
        patientId: auth.user.id,
        channel: 'WECHAT',
        channelTradeNo: `wx-${item.businessType.toLowerCase()}-${item.businessId}-${Date.now()}`
      }
    });
    uni.showToast({ title: '缴费成功', icon: 'success' });
    await load();
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' });
  }
}

onShow(load);
</script>

<style scoped>
.summary-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20rpx;
  margin-bottom: 20rpx;
}

.warning-card {
  border: 1px solid #fed7aa;
  background: #fff7ed;
}

.warning-title {
  color: #c2410c;
  font-size: 28rpx;
  font-weight: 700;
  margin-bottom: 10rpx;
}

.summary-card {
  margin-bottom: 0;
}

.summary-label {
  color: #64748b;
  font-size: 24rpx;
}

.summary-value {
  margin-top: 12rpx;
  color: #0f766e;
  font-size: 42rpx;
  font-weight: 700;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}

.section-title {
  color: #0f766e;
  font-size: 30rpx;
  font-weight: 700;
}

.category-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16rpx;
}

.category-card {
  padding: 18rpx;
  border-radius: 18rpx;
  background: #f8fafc;
}

.category-name {
  color: #334155;
  font-size: 26rpx;
  font-weight: 600;
}

.category-amount {
  margin: 10rpx 0 6rpx;
  color: #b45309;
  font-size: 32rpx;
  font-weight: 700;
}

.item-row {
  display: flex;
  justify-content: space-between;
  gap: 20rpx;
  padding: 22rpx 0;
  border-bottom: 1px solid #e5e7eb;
}

.item-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.item-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.item-title {
  font-size: 30rpx;
  font-weight: 700;
}

.item-desc {
  color: #334155;
  font-size: 26rpx;
}

.item-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  gap: 12rpx;
}

.amount {
  color: #b45309;
  font-size: 30rpx;
  font-weight: 700;
}

.mini-pay {
  margin: 0;
  min-width: 160rpx;
  padding: 0 20rpx;
  line-height: 2.2;
}
</style>

<template>
  <patient-nav-bar :title="mode === 'record' ? '缴费退费记录' : '待缴费项目'" />
  <view class="page">
    <view v-if="mode === 'record'">
      <view
        v-for="item in sortedFinancialRecords"
        :key="`${item.kind}-${item.id}`"
        :class="['card', 'item-row', 'payment-record', { payable: isPendingFinancialRecord(item), refund: item.kind === 'refund' }]"
        @click="openPendingPayment(item)"
      >
        <view class="item-main">
          <view class="item-title">{{ paymentRecordTitle(item) }}</view>
          <view class="item-desc">{{ paymentRecordDescription(item) }}</view>
          <view v-if="paymentRecordNote(item)" class="muted">{{ paymentRecordNote(item) }}</view>
          <view v-if="isInsurancePayment(item)" class="insurance-badge">医保支付</view>
          <view class="muted">{{ financialRecordTimeText(item) }}</view>
        </view>
        <view class="item-actions">
          <view :class="['amount', { 'refund-amount': item.kind === 'refund' }]">{{ item.kind === 'refund' ? '退' : '' }}¥{{ amountText(item.amount) }}</view>
          <view :class="['record-status', item.status.toLowerCase()]">{{ paymentStatusLabel(item.status) }}</view>
        </view>
      </view>
      <view v-if="!sortedFinancialRecords.length" class="card muted">暂无缴费退费记录</view>
    </view>

    <view v-else>
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
        <view class="summary-label">医保参考自付</view>
        <view class="summary-value insurance-value">¥{{ amountText(totalInsuranceAmount) }}</view>
      </view>
      <view class="card summary-card">
        <view class="summary-label">待缴项目数</view>
        <view class="summary-value">{{ totalCount }}</view>
      </view>
    </view>

    <view class="card">
      <view class="section-head">
        <view class="section-title">费用概览</view>
      </view>
      <view class="category-grid">
        <view v-for="item in categorySummaries" :key="item.key" class="category-card">
          <view class="category-left">
            <text class="category-name">{{ item.label }}</text>
            <text class="category-count">{{ item.count }} 项</text>
          </view>
          <view class="category-amount">¥{{ amountText(item.amount) }}</view>
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
          <view class="insurance-price">医保参考 ¥{{ amountText(item.insuranceAmount) }}</view>
          <button class="button mini-pay" @click="openPaymentDialog(item)">去缴费</button>
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
          <view class="insurance-price">医保参考 ¥{{ amountText(item.insuranceAmount) }}</view>
          <button class="button mini-pay" @click="openPaymentDialog(item)">去缴费</button>
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
          <view class="insurance-price">医保参考 ¥{{ amountText(item.insuranceAmount) }}</view>
          <button class="button mini-pay" @click="openPaymentDialog(item)">去缴费</button>
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
          <view class="insurance-price">医保参考 ¥{{ amountText(item.insuranceAmount) }}</view>
          <button class="button mini-pay" @click="openPaymentDialog(item)">去缴费</button>
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
          <view class="insurance-price">医保参考 ¥{{ amountText(item.insuranceAmount) }}</view>
          <button class="button mini-pay" @click="openPaymentDialog(item)">去缴费</button>
        </view>
      </view>
    </view>

    <view v-if="!totalCount" class="card muted">暂无待缴费项目</view>
    </view>

    <view v-if="paymentDialogVisible && selectedPaymentItem" class="payment-mask">
      <view class="payment-dialog" @tap.stop>
        <view class="dialog-title">选择支付方式</view>
        <view class="dialog-subtitle">{{ selectedPaymentItem.title }}</view>
        <view class="dialog-amount">
          <text>{{ dialogPaymentMethod === 'MEDICAL_INSURANCE' ? '医保参考自付' : '应付金额' }}</text>
          <strong>¥{{ amountText(dialogPayAmount) }}</strong>
        </view>
        <view class="method-list">
          <view
            :class="['method-button', dialogPaymentMethod === 'WECHAT' && 'method-button--active']"
            @tap.stop="dialogPaymentMethod = 'WECHAT'"
          >
            微信支付
          </view>
          <view
            :class="['method-button', dialogPaymentMethod === 'MEDICAL_INSURANCE' && 'method-button--active', !insuranceBound && 'method-button--disabled']"
            @tap.stop="selectDialogInsurancePayment()"
          >
            医保卡支付
          </view>
        </view>
        <view v-if="!insuranceBound" class="insurance-tip">
          医保卡需要先前往就诊人管理页面完成微信医保认证。
          <text class="insurance-link" @tap.stop="goInsuranceBinding()">去绑定认证</text>
        </view>
        <view v-else class="insurance-tip insurance-tip--ok">当前就诊人已完成医保认证。</view>
        <view class="dialog-actions">
          <button class="dialog-cancel" @tap.stop="closePaymentDialog()">取消</button>
          <button class="dialog-confirm" @tap.stop="confirmPayment()">确认缴费</button>
        </view>
      </view>
    </view>
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
  status: string;
  paymentStatus: string;
  createdAt?: string;
}

interface PaymentOrder {
  id: string;
  businessType: string;
  businessId: string;
  amount: number;
  status: string;
  paymentMethod?: string;
  createdAt?: string;
  paidAt?: string;
}

interface RefundOrder {
  id: string;
  businessType: string;
  businessId: string;
  patientId: string;
  amount: number;
  reason?: string;
  status: string;
  operatorId?: string;
  refundedAt?: string;
}

type FinancialRecord =
  | (PaymentOrder & { kind: 'payment' })
  | (RefundOrder & { kind: 'refund' });

interface MedicalOrder {
  id: string;
  orderType: 'CHECK' | 'LAB' | 'DISPOSAL';
  itemName: string;
  amount: number;
  paymentStatus: string;
  status: string;
  urgency: string;
  purpose?: string;
  bodyPart?: string;
  roomLocation?: string;
  createdAt?: string;
}

interface PrescriptionItem {
  id: string;
  drugName: string;
  quantity: number;
}

interface Prescription {
  id: string;
  prescriptionNo: string;
  diagnosis: string;
  totalAmount: number;
  status: 'DRAFT' | 'CONFIRMED' | 'PENDING_PAYMENT' | 'PAID' | 'WAITING_DISPENSE' | 'DISPENSED' | 'RETURNED' | 'RETURN_PENDING_REFUND' | 'RETURN_REFUNDED' | 'CANCELLED';
  items?: PrescriptionItem[];
  confirmedAt?: string;
  createdAt?: string;
}

interface PendingItem {
  businessType: 'APPOINTMENT' | 'MEDICAL_ORDER' | 'PRESCRIPTION';
  businessId: string;
  feeType: 'REGISTRATION' | 'CHECK' | 'LAB' | 'DISPOSAL' | 'DRUG';
  title: string;
  description: string;
  note?: string;
  amount: number;
  insuranceAmount: number;
  sortTime: string;
}

const auth = useAuthStore();
const registrationItems = ref<PendingItem[]>([]);
const medicalOrderItems = ref<PendingItem[]>([]);
const prescriptionItems = ref<PendingItem[]>([]);
const paymentRecords = ref<PaymentOrder[]>([]);
const refundRecords = ref<RefundOrder[]>([]);
const recordAppointments = ref<Appointment[]>([]);
const recordMedicalOrders = ref<MedicalOrder[]>([]);
const recordPrescriptions = ref<Prescription[]>([]);
const loadWarning = ref('');
const mode = ref<'pending' | 'record'>('pending');
const paymentDialogVisible = ref(false);
const selectedPaymentItem = ref<PendingItem | null>(null);
const dialogPaymentMethod = ref<'WECHAT' | 'MEDICAL_INSURANCE'>('WECHAT');
const registeredAppointmentStatuses = new Set(['WAITING', 'CALLED', 'IN_VISIT', 'REVISIT_WAITING']);

const checkItems = computed(() => medicalOrderItems.value.filter((item) => item.feeType === 'CHECK'));
const labItems = computed(() => medicalOrderItems.value.filter((item) => item.feeType === 'LAB'));
const disposalItems = computed(() => medicalOrderItems.value.filter((item) => item.feeType === 'DISPOSAL'));
const recordAppointmentMap = computed(() => new Map(recordAppointments.value.map((item) => [item.id, item])));
const recordMedicalOrderMap = computed(() => new Map(recordMedicalOrders.value.map((item) => [item.id, item])));
const recordPrescriptionMap = computed(() => new Map(recordPrescriptions.value.map((item) => [item.id, item])));

const totalAmount = computed(() =>
  [...registrationItems.value, ...medicalOrderItems.value, ...prescriptionItems.value]
    .reduce((sum, item) => sum + Number(item.amount ?? 0), 0)
);
const totalInsuranceAmount = computed(() =>
  [...registrationItems.value, ...medicalOrderItems.value, ...prescriptionItems.value]
    .reduce((sum, item) => sum + Number(item.insuranceAmount ?? 0), 0)
);
const totalCount = computed(() => registrationItems.value.length + medicalOrderItems.value.length + prescriptionItems.value.length);
const insuranceBound = computed(() => auth.isMedicalInsuranceBound(auth.boundPatient?.id));
const dialogPayAmount = computed(() => {
  if (!selectedPaymentItem.value) return 0;
  return payableAmount(selectedPaymentItem.value, dialogPaymentMethod.value);
});

const sortedFinancialRecords = computed(() =>
  [
    ...paymentRecords.value.map((item) => ({ ...item, kind: 'payment' as const })),
    ...refundRecords.value.map((item) => ({ ...item, kind: 'refund' as const }))
  ].sort((left, right) => {
    const statusDiff = paymentRecordStatusRank(left) - paymentRecordStatusRank(right);
    if (statusDiff !== 0) {
      return statusDiff;
    }
    return paymentRecordSortTime(right).localeCompare(paymentRecordSortTime(left));
  })
);

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

function insuranceAmount(item: Pick<PendingItem, 'feeType' | 'amount'>) {
  const ratio = item.feeType === 'REGISTRATION' ? 0.7 : 0.6;
  const value = Math.max(0.01, Number(item.amount ?? 0) * ratio);
  return Math.round(value * 100) / 100;
}

function payableAmount(item: PendingItem, method: 'WECHAT' | 'MEDICAL_INSURANCE') {
  return method === 'MEDICAL_INSURANCE' ? item.insuranceAmount : item.amount;
}

function openPaymentDialog(item: PendingItem) {
  selectedPaymentItem.value = item;
  dialogPaymentMethod.value = 'WECHAT';
  paymentDialogVisible.value = true;
}

function closePaymentDialog() {
  paymentDialogVisible.value = false;
  selectedPaymentItem.value = null;
  dialogPaymentMethod.value = 'WECHAT';
}

function selectDialogInsurancePayment() {
  if (!insuranceBound.value) {
    uni.showToast({ title: '请先前往就诊人管理绑定认证', icon: 'none' });
    return;
  }
  dialogPaymentMethod.value = 'MEDICAL_INSURANCE';
}

function goInsuranceBinding() {
  closePaymentDialog();
  uni.navigateTo({ url: '/pages/real-name/index' });
}

async function confirmPayment() {
  if (!selectedPaymentItem.value) return;
  await pay(selectedPaymentItem.value, dialogPaymentMethod.value);
}

function businessTypeLabel(type: string) {
  return {
    APPOINTMENT: '挂号费',
    MEDICAL_ORDER: '检查检验处置费',
    PRESCRIPTION: '药费'
  }[type] ?? type;
}

function paymentRecordTitle(item: FinancialRecord) {
  if (item.kind === 'refund') {
    return `${businessTypeLabel(item.businessType)}退费`;
  }
  if (item.businessType === 'APPOINTMENT') {
    const appointment = recordAppointmentMap.value.get(item.businessId);
    return appointment ? `${appointment.departmentName} · ${appointment.doctorName}` : businessTypeLabel(item.businessType);
  }
  if (item.businessType === 'MEDICAL_ORDER') {
    const order = recordMedicalOrderMap.value.get(item.businessId);
    return order?.itemName || businessTypeLabel(item.businessType);
  }
  if (item.businessType === 'PRESCRIPTION') {
    const prescription = recordPrescriptionMap.value.get(item.businessId);
    return prescription?.diagnosis ? `${prescription.diagnosis}药费` : businessTypeLabel(item.businessType);
  }
  return businessTypeLabel(item.businessType);
}

function paymentRecordDescription(item: FinancialRecord) {
  if (item.kind === 'refund') {
    const base = paymentRecordBusinessDescription(item);
    return item.reason ? `${base} · ${item.reason}` : base;
  }
  return paymentRecordBusinessDescription(item);
}

function paymentRecordBusinessDescription(item: FinancialRecord) {
  if (item.businessType === 'APPOINTMENT') {
    const appointment = recordAppointmentMap.value.get(item.businessId);
    if (appointment) {
      return `${appointment.visitDate} ${normalizeStartTime(appointment.startTime) || appointment.period}`;
    }
  }
  if (item.businessType === 'MEDICAL_ORDER') {
    const order = recordMedicalOrderMap.value.get(item.businessId);
    if (order) {
      const details = [order.bodyPart, order.purpose, order.roomLocation].filter(Boolean).join(' · ');
      return `${orderTypeLabel(order.orderType)}${details ? ` · ${details}` : ''}`;
    }
  }
  if (item.businessType === 'PRESCRIPTION') {
    const prescription = recordPrescriptionMap.value.get(item.businessId);
    if (prescription) {
      const drugs = (prescription.items ?? [])
        .slice(0, 3)
        .map((drug) => `${drug.drugName}×${drug.quantity}`)
        .join('、');
      return drugs || prescription.diagnosis || '处方药费';
    }
  }
  return '业务详情加载中';
}

function paymentRecordNote(item: FinancialRecord) {
  if (item.kind === 'refund') {
    return item.operatorId ? `退费操作员：${item.operatorId}` : '';
  }
  const methodText = item.paymentMethod ? `支付方式：${paymentMethodLabel(item.paymentMethod)}；` : '';
  if (item.businessType === 'APPOINTMENT') {
    const appointment = recordAppointmentMap.value.get(item.businessId);
    return appointment ? `${methodText}挂号状态：${appointmentStatusLabel(appointment)}` : methodText;
  }
  if (item.businessType === 'MEDICAL_ORDER') {
    const order = recordMedicalOrderMap.value.get(item.businessId);
    return order ? `${methodText}项目状态：${medicalOrderStatusLabel(order.status, order.paymentStatus)}` : methodText;
  }
  if (item.businessType === 'PRESCRIPTION') {
    const prescription = recordPrescriptionMap.value.get(item.businessId);
    return prescription ? `${methodText}处方状态：${prescriptionStatusLabel(prescription.status)}` : methodText;
  }
  return methodText;
}

function isInsurancePayment(item: FinancialRecord) {
  return item.kind === 'payment' && item.paymentMethod?.includes('MEDICAL_INSURANCE');
}

function paymentMethodLabel(method?: string) {
  return {
    WECHAT: '微信支付',
    WECHAT_TEST: '微信支付',
    MEDICAL_INSURANCE: '医保卡支付',
    MEDICAL_INSURANCE_TEST: '医保卡支付'
  }[method ?? ''] ?? (method || '模拟支付');
}

function paymentStatusLabel(status: string) {
  return {
    PENDING: '待支付',
    PENDING_PAYMENT: '待缴费',
    UNPAID: '未支付',
    PAID: '已支付',
    FAILED: '支付失败',
    CANCELLED: '已取消',
    REFUNDED: '已退款'
  }[status] ?? status;
}

function paymentRecordSortTime(item: FinancialRecord) {
  return item.kind === 'refund' ? item.refundedAt || '' : item.paidAt || item.createdAt || '';
}

function financialRecordTimeText(item: FinancialRecord) {
  if (item.kind === 'refund') {
    return item.refundedAt ? `退费时间：${formatDateTime(item.refundedAt)}` : `当前状态：${paymentStatusLabel(item.status)}`;
  }
  return item.paidAt ? `支付时间：${formatDateTime(item.paidAt)}` : `当前状态：${paymentStatusLabel(item.status)}`;
}

function isPendingFinancialRecord(item: FinancialRecord) {
  return item.kind === 'payment' && ['PENDING', 'PENDING_PAYMENT', 'UNPAID'].includes(item.status);
}

function paymentRecordStatusRank(item: FinancialRecord) {
  return isPendingFinancialRecord(item) ? 0 : 1;
}

function pendingAppointmentSortTime(item: Appointment) {
  const startTime = normalizeStartTime(item.startTime) || '00:00';
  return item.visitDate ? `${item.visitDate}T${startTime}:00` : item.createdAt || '';
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

function openPendingPayment(item: FinancialRecord) {
  if (!isPendingFinancialRecord(item)) return;
  uni.navigateTo({ url: '/pages/pending-payments/index' });
}

onLoad((options) => {
  mode.value = options?.mode === 'record' ? 'record' : 'pending';
  uni.setNavigationBarTitle({ title: mode.value === 'record' ? '缴费退费记录' : '待缴费项目' });
});

function appointmentStatusLabel(item: Appointment) {
  if (item.paymentStatus === 'REFUNDED') return '已退号';
  return {
    PENDING_PAYMENT: '待缴费',
    WAITING: '待就诊',
    CALLED: '已叫号',
    IN_VISIT: '就诊中',
    REVISIT_WAITING: '复诊等待',
    CANCELLED: '已取消',
    FINISHED: '已完成',
    EXPIRED: '已过期'
  }[item.status] ?? item.status;
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

function medicalOrderStatusLabel(status: MedicalOrder['status'], paymentStatus: MedicalOrder['paymentStatus']) {
  if (paymentStatus === 'UNPAID') return '待缴费';
  return {
    PENDING_PAYMENT: '待缴费',
    WAITING_TRIAGE: '待安排',
    WAITING: '待执行',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成',
    MISSED: '已顺延'
  }[status] ?? status;
}

function prescriptionStatusLabel(status: Prescription['status']) {
  return {
    DRAFT: '草稿',
    CONFIRMED: '已开立，待缴费',
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
  loadWarning.value = '';
  const patientQuery = `patientId=${encodeURIComponent(patient.id)}`;
  if (mode.value === 'record') {
    const [paymentsResult, refundsResult, appointmentsResult, medicalOrdersResult, prescriptionsResult] = await Promise.allSettled([
      request<PaymentOrder[]>({ url: `/payments?${patientQuery}`, method: 'GET' }),
      request<RefundOrder[]>({ url: `/refunds?${patientQuery}`, method: 'GET' }),
      request<Appointment[]>({ url: `/appointments?${patientQuery}`, method: 'GET' }),
      request<MedicalOrder[]>({ url: `/medical-orders?${patientQuery}&view=PAYMENT_RECORD`, method: 'GET' }),
      request<Prescription[]>({ url: `/prescriptions?${patientQuery}&view=PAYMENT_RECORD`, method: 'GET' })
    ]);

    paymentRecords.value = paymentsResult.status === 'fulfilled' ? paymentsResult.value : [];
    refundRecords.value = refundsResult.status === 'fulfilled' ? refundsResult.value : [];
    recordAppointments.value = appointmentsResult.status === 'fulfilled' ? appointmentsResult.value : [];
    recordMedicalOrders.value = medicalOrdersResult.status === 'fulfilled' ? medicalOrdersResult.value : [];
    recordPrescriptions.value = prescriptionsResult.status === 'fulfilled' ? prescriptionsResult.value : [];

    const failedLabels = [
      paymentsResult.status === 'rejected' ? '缴费记录' : '',
      refundsResult.status === 'rejected' ? '退费记录' : '',
      appointmentsResult.status === 'rejected' ? '挂号详情' : '',
      medicalOrdersResult.status === 'rejected' ? '检查检验处置详情' : '',
      prescriptionsResult.status === 'rejected' ? '处方详情' : ''
    ].filter(Boolean);
    loadWarning.value = failedLabels.length ? `当前未成功加载：${failedLabels.join('、')}。` : '';
    return;
  }
  const warnings: string[] = [];
  const [appointmentsResult, paymentsResult, medicalOrdersResult, prescriptionsResult] = await Promise.allSettled([
    request<Appointment[]>({ url: `/appointments?status=PENDING_PAYMENT&${patientQuery}`, method: 'GET' }),
    request<PaymentOrder[]>({ url: `/payments?businessType=APPOINTMENT&status=PENDING&${patientQuery}`, method: 'GET' }),
    request<MedicalOrder[]>({ url: `/medical-orders?status=PENDING_PAYMENT&${patientQuery}&view=OUTPATIENT_PAYMENT`, method: 'GET' }),
    request<Prescription[]>({ url: `/prescriptions?${patientQuery}&view=OUTPATIENT_PAYMENT`, method: 'GET' })
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
      note: `当前状态：${appointmentStatusLabel(item)}，挂号后需完成缴费才能正常就诊`,
      amount: paymentByBusinessId.get(item.id)?.amount ?? 0.01,
      insuranceAmount: insuranceAmount({
        feeType: 'REGISTRATION',
        amount: paymentByBusinessId.get(item.id)?.amount ?? 0.01
      }),
      sortTime: pendingAppointmentSortTime(item)
    }))
    .sort((left, right) => right.sortTime.localeCompare(left.sortTime));

  medicalOrderItems.value = medicalOrders
    .filter((item) => item.paymentStatus === 'UNPAID')
    .map((item) => ({
      businessType: 'MEDICAL_ORDER' as const,
      businessId: item.id,
      feeType: item.orderType,
      title: item.itemName,
      description: `${orderTypeLabel(item.orderType)} · ${urgencyLabel(item.urgency)}`,
      note: `当前状态：${medicalOrderStatusLabel(item.status, item.paymentStatus)}`,
      amount: item.amount,
      insuranceAmount: insuranceAmount({ feeType: item.orderType, amount: item.amount }),
      sortTime: item.createdAt || ''
    }))
    .sort((left, right) => right.sortTime.localeCompare(left.sortTime));

  prescriptionItems.value = prescriptions
    .filter((item) => item.status === 'PENDING_PAYMENT' || item.status === 'CONFIRMED')
    .map((item) => ({
      businessType: 'PRESCRIPTION' as const,
      businessId: item.id,
      feeType: 'DRUG' as const,
      title: '药费',
      description: item.diagnosis || '待医生确认诊断',
      note: `当前状态：${prescriptionStatusLabel(item.status)}`,
      amount: item.totalAmount,
      insuranceAmount: insuranceAmount({ feeType: 'DRUG', amount: item.totalAmount }),
      sortTime: item.confirmedAt || item.createdAt || ''
    }))
    .sort((left, right) => right.sortTime.localeCompare(left.sortTime));

  function unwrapResult<T>(result: PromiseSettledResult<T>, fallback: T, label: string): T {
    if (result.status === 'fulfilled') {
      return result.value;
    }
    warnings.push(label);
    console.error(`pending-payments load failed: ${label}`, result.reason);
    return fallback;
  }
}

async function pay(item: PendingItem, method: 'WECHAT' | 'MEDICAL_INSURANCE') {
  let patient;
  try {
    patient = auth.requireBoundPatient();
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' });
    uni.navigateTo({ url: '/pages/real-name/index' });
    return;
  }
  try {
    if (method === 'MEDICAL_INSURANCE' && !insuranceBound.value) {
      uni.showToast({ title: '请先前往就诊人管理绑定认证', icon: 'none' });
      return;
    }
    const channelTradePrefix = method === 'MEDICAL_INSURANCE' ? 'mi' : 'wx';
    await request({
      url: '/payments/orders',
      method: 'POST',
      data: {
        businessType: item.businessType,
        businessId: item.businessId,
        patientId: patient.id,
        amount: payableAmount(item, method),
        paymentMethod: `${method}_TEST`
      }
    });
    await request({
      url: '/payments/test-callback',
      method: 'POST',
      data: {
        businessType: item.businessType,
        businessId: item.businessId,
        patientId: patient.id,
        channel: method,
        channelTradeNo: `${channelTradePrefix}-${item.businessType.toLowerCase()}-${item.businessId}-${Date.now()}`
      }
    });
    uni.showToast({ title: method === 'MEDICAL_INSURANCE' ? '医保缴费成功' : '缴费成功', icon: 'success' });
    closePaymentDialog();
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
  grid-template-columns: repeat(3, minmax(0, 1fr));
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

.insurance-value {
  color: #15803d;
}

.method-button {
  flex: 1;
  min-width: 0;
  height: 64rpx;
  padding: 0 18rpx;
  border: 1px solid #dbe6ef;
  border-radius: 12rpx;
  background: #f8fafc;
  color: #334155;
  font-size: 24rpx;
  font-weight: 700;
  line-height: 64rpx;
  text-align: center;
}

.method-button--active {
  border-color: #0f766e;
  background: #ecfdf5;
  color: #0f766e;
}

.method-button--disabled {
  color: #94a3b8;
  background: #f1f5f9;
}

.payment-mask {
  position: fixed;
  inset: 0;
  z-index: 20;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding: 24rpx;
  background: rgba(15, 23, 42, 0.42);
}

.payment-dialog {
  width: 100%;
  padding: 30rpx;
  border-radius: 24rpx;
  background: #fff;
  box-shadow: 0 24rpx 60rpx rgba(15, 23, 42, 0.2);
}

.dialog-title {
  color: #0f172a;
  font-size: 34rpx;
  font-weight: 800;
}

.dialog-subtitle {
  margin-top: 8rpx;
  color: #64748b;
  font-size: 26rpx;
}

.dialog-amount {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 24rpx;
  padding: 20rpx;
  border-radius: 16rpx;
  background: #f8fafc;
}

.dialog-amount text {
  color: #64748b;
  font-size: 26rpx;
}

.dialog-amount strong {
  color: #b45309;
  font-size: 42rpx;
}

.method-list {
  display: flex;
  gap: 16rpx;
  margin-top: 24rpx;
}

.insurance-tip {
  margin-top: 18rpx;
  color: #c2410c;
  font-size: 25rpx;
  line-height: 1.6;
}

.insurance-tip--ok {
  color: #15803d;
}

.insurance-link {
  margin-left: 10rpx;
  color: #0f766e;
  font-weight: 800;
}

.dialog-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
  margin-top: 28rpx;
}

.dialog-cancel,
.dialog-confirm {
  height: 76rpx;
  margin: 0;
  border-radius: 12rpx;
  font-size: 28rpx;
  font-weight: 800;
  line-height: 76rpx;
}

.dialog-cancel {
  background: #f1f5f9;
  color: #475569;
}

.dialog-confirm {
  background: #0f766e;
  color: #fff;
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
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.category-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18rpx;
  border-radius: 18rpx;
  background: #f8fafc;
}

.category-left {
  display: flex;
  align-items: baseline;
  gap: 10rpx;
}
  color: #334155;
  font-size: 26rpx;
  font-weight: 600;
}

.category-amount {
  color: #b45309;
  font-size: 32rpx;
  font-weight: 700;
}

.category-count {
  color: #64748b;
  font-size: 24rpx;
  margin-left: 12rpx;
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

.card.item-row {
  padding: 26rpx 30rpx;
  border-bottom: none;
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

.insurance-price {
  color: #15803d;
  font-size: 23rpx;
  font-weight: 700;
}

.insurance-badge {
  align-self: flex-start;
  padding: 5rpx 14rpx;
  border-radius: 999rpx;
  background: #dcfce7;
  color: #166534;
  font-size: 22rpx;
  font-weight: 700;
}

.refund-amount {
  color: #0f766e;
}

.mini-pay {
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  min-width: 160rpx;
  height: 64rpx;
  padding: 0 20rpx;
  line-height: 64rpx;
}

.record-status {
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 700;
}

.record-status.pending {
  background: #fff7ed;
  color: #c2410c;
}

.record-status.paid {
  background: #dcfce7;
  color: #166534;
}

.record-status.failed {
  background: #fee2e2;
  color: #b91c1c;
}

.record-status.refunded {
  background: #f1f5f9;
  color: #64748b;
}
</style>

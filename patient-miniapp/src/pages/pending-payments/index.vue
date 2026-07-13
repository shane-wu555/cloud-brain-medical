<template>
  <patient-nav-bar :title="mode === 'record' ? '缴费退费记录' : '门诊缴费'" />
  <view class="page">
    <view v-if="mode === 'record'">
      <view
        v-for="item in sortedFinancialRecords"
        :key="`${item.kind}-${item.id}`"
        :class="['card', 'item-row', 'payment-record', { payable: isPendingFinancialRecord(item), refund: item.kind === 'refund' }]"
        @tap="openPendingPayment(item)"
      >
        <view class="item-main">
          <view class="item-title">{{ paymentRecordTitle(item) }}</view>
          <view class="item-desc">{{ paymentRecordDescription(item) }}</view>
          <view
            v-for="(note, index) in paymentRecordNotes(item)"
            :key="`${item.kind}-${item.id}-note-${index}`"
            class="muted"
          >
            {{ note }}
          </view>
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

    <view v-else @touchstart="onPaymentTouchStart" @touchend="onPaymentTouchEnd">
    <view v-if="loadWarning" class="card warning-card">
      <view class="warning-title">部分数据加载失败</view>
      <view class="muted">{{ loadWarning }}</view>
    </view>

    <view class="summary-grid">
      <view class="card summary-card">
        <view class="summary-label">{{ activePaymentTab === 'paid' ? '已缴总金额' : '待缴总金额' }}</view>
        <view class="summary-value">¥{{ amountText(activeTabAmount) }}</view>
      </view>
      <view class="card summary-card">
        <view class="summary-label">医保参考自付</view>
        <view class="summary-value insurance-value">¥{{ amountText(activeTabInsuranceAmount) }}</view>
      </view>
      <view class="card summary-card">
        <view class="summary-label">{{ activePaymentTab === 'paid' ? '已缴项目数' : '待缴项目数' }}</view>
        <view class="summary-value">{{ activeTabCount }}</view>
      </view>
    </view>

    <view class="payment-tabs">
      <view
        :class="['payment-tab', activePaymentTab === 'unpaid' && 'active']"
        @tap="activePaymentTab = 'unpaid'"
      >
        <text>未缴费</text>
        <em>{{ pendingCount }} 项</em>
      </view>
      <view
        :class="['payment-tab', activePaymentTab === 'paid' && 'active']"
        @tap="activePaymentTab = 'paid'"
      >
        <text>已缴费</text>
        <em>{{ paidCount }} 项</em>
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

    <view v-for="section in activeCategorySections" :key="section.key" :class="['card', 'fee-card', section.cardClass]">
      <view class="section-head">
        <view :class="['section-icon', section.iconClass]">{{ section.icon }}</view>
        <view class="section-title">{{ section.label }}</view>
        <view class="section-count">{{ section.items.length }} 项</view>
      </view>
      <view v-for="item in section.items" :key="item.businessId" class="item-row">
        <view class="item-main">
          <view class="item-title">{{ item.title }}</view>
          <view class="item-desc">{{ item.description }}</view>
          <view v-if="item.note" :class="['fee-status-badge', item.payable ? 'pending-badge' : 'paid-badge']">{{ item.note }}</view>
        </view>
        <view class="item-actions">
          <view class="amount">¥{{ amountText(item.amount) }}</view>
          <view class="insurance-price">医保参考 ¥{{ amountText(item.insuranceAmount) }}</view>
          <button v-if="item.payable" class="button mini-pay" @tap="openPaymentDialog(item)">去缴费</button>
        </view>
      </view>
    </view>

    <view v-if="!activeTabCount" class="card muted">{{ activePaymentTab === 'paid' ? '暂无已缴费项目' : '暂无未缴费项目' }}</view>
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
  payable: boolean;
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
const activePaymentTab = ref<'unpaid' | 'paid'>('unpaid');
const paymentDialogVisible = ref(false);
const selectedPaymentItem = ref<PendingItem | null>(null);
const dialogPaymentMethod = ref<'WECHAT' | 'MEDICAL_INSURANCE'>('WECHAT');
const registeredAppointmentStatuses = new Set(['WAITING', 'CALLED', 'IN_VISIT', 'REVISIT_WAITING']);
let paymentTouchStartX = 0;

const checkItems = computed(() => medicalOrderItems.value.filter((item) => item.feeType === 'CHECK'));
const labItems = computed(() => medicalOrderItems.value.filter((item) => item.feeType === 'LAB'));
const disposalItems = computed(() => medicalOrderItems.value.filter((item) => item.feeType === 'DISPOSAL'));
const recordAppointmentMap = computed(() => new Map(recordAppointments.value.map((item) => [item.id, item])));
const recordMedicalOrderMap = computed(() => new Map(recordMedicalOrders.value.map((item) => [item.id, item])));
const recordPrescriptionMap = computed(() => new Map(recordPrescriptions.value.map((item) => [item.id, item])));
const allPaymentItems = computed(() => [...registrationItems.value, ...medicalOrderItems.value, ...prescriptionItems.value]);
const pendingPaymentItems = computed(() => allPaymentItems.value.filter((item) => item.payable));
const paidPaymentItems = computed(() => allPaymentItems.value.filter((item) => !item.payable));
const activePaymentItems = computed(() => activePaymentTab.value === 'paid' ? paidPaymentItems.value : pendingPaymentItems.value);

const totalAmount = computed(() =>
  pendingPaymentItems.value
    .reduce((sum, item) => sum + Number(item.amount ?? 0), 0)
);
const totalInsuranceAmount = computed(() =>
  pendingPaymentItems.value
    .reduce((sum, item) => sum + Number(item.insuranceAmount ?? 0), 0)
);
const totalCount = computed(() => registrationItems.value.length + medicalOrderItems.value.length + prescriptionItems.value.length);
const pendingCount = computed(() => pendingPaymentItems.value.length);
const paidCount = computed(() => paidPaymentItems.value.length);
const activeTabAmount = computed(() => activePaymentItems.value.reduce((sum, item) => sum + Number(item.amount ?? 0), 0));
const activeTabInsuranceAmount = computed(() => activePaymentItems.value.reduce((sum, item) => sum + Number(item.insuranceAmount ?? 0), 0));
const activeTabCount = computed(() => activePaymentItems.value.length);
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
  summarizeCategory('registration', '挂号费', activeSectionItems(registrationItems.value)),
  summarizeCategory('check', '检查费', activeSectionItems(checkItems.value)),
  summarizeCategory('lab', '检验费', activeSectionItems(labItems.value)),
  summarizeCategory('disposal', '处置费', activeSectionItems(disposalItems.value)),
  summarizeCategory('drug', '药费', activeSectionItems(prescriptionItems.value))
]);

const activeCategorySections = computed(() => [
  {
    key: 'registration',
    label: '挂号费',
    icon: '号',
    iconClass: 'reg-icon',
    cardClass: 'fee-registration',
    items: activeSectionItems(registrationItems.value)
  },
  {
    key: 'check',
    label: '检查费',
    icon: '查',
    iconClass: 'check-icon',
    cardClass: 'fee-check',
    items: activeSectionItems(checkItems.value)
  },
  {
    key: 'lab',
    label: '检验费',
    icon: '检',
    iconClass: 'lab-icon',
    cardClass: 'fee-lab',
    items: activeSectionItems(labItems.value)
  },
  {
    key: 'disposal',
    label: '处置费',
    icon: '处',
    iconClass: 'disposal-icon',
    cardClass: 'fee-disposal',
    items: activeSectionItems(disposalItems.value)
  },
  {
    key: 'drug',
    label: '药费',
    icon: '药',
    iconClass: 'drug-icon',
    cardClass: 'fee-drug',
    items: activeSectionItems(prescriptionItems.value)
  }
].filter((section) => section.items.length > 0));

function activeSectionItems(items: PendingItem[]) {
  return items.filter((item) => activePaymentTab.value === 'paid' ? !item.payable : item.payable);
}

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
      const details = [order.bodyPart, order.roomLocation].filter(Boolean).join(' · ');
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

function paymentRecordNotes(item: FinancialRecord) {
  if (item.kind === 'refund') {
    return item.operatorId ? [`退费操作员：${item.operatorId}`] : [];
  }
  const notes: string[] = [];
  if (item.paymentMethod) {
    notes.push(`支付方式：${paymentMethodLabel(item.paymentMethod)}`);
  }
  if (item.businessType === 'APPOINTMENT') {
    const appointment = recordAppointmentMap.value.get(item.businessId);
    if (appointment) {
      notes.push(`挂号状态：${appointmentStatusLabel(appointment)}`);
    }
    return notes;
  }
  if (item.businessType === 'MEDICAL_ORDER') {
    const order = recordMedicalOrderMap.value.get(item.businessId);
    if (order) {
      notes.push(`项目状态：${medicalOrderStatusLabel(order.status, order.paymentStatus)}`);
    }
    return notes;
  }
  if (item.businessType === 'PRESCRIPTION') {
    const prescription = recordPrescriptionMap.value.get(item.businessId);
    if (prescription) {
      notes.push(`处方状态：${prescriptionStatusLabel(prescription.status)}`);
    }
    return notes;
  }
  return notes;
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

function isPayableAppointment(item: Appointment) {
  return item.paymentStatus === 'UNPAID' || item.paymentStatus === 'FAILED';
}

function isVisibleAppointmentPayment(item: Appointment) {
  return isPayableAppointment(item) || item.paymentStatus === 'PAID';
}

function isPayableMedicalOrder(item: MedicalOrder) {
  return item.paymentStatus === 'UNPAID';
}

function isVisibleMedicalOrderPayment(item: MedicalOrder) {
  return isPayableMedicalOrder(item) || item.paymentStatus === 'PAID';
}

function isPayablePrescription(item: Prescription) {
  return item.status === 'PENDING_PAYMENT' || item.status === 'CONFIRMED';
}

function isVisiblePrescriptionPayment(item: Prescription) {
  return isPayablePrescription(item) || ['PAID', 'WAITING_DISPENSE', 'DISPENSED'].includes(item.status);
}

function feePaymentNote(payable: boolean) {
  return payable ? '待缴费' : '已缴费';
}

function feeStatusRank(item: PendingItem) {
  return item.payable ? 0 : 1;
}

function sortFeeItems(left: PendingItem, right: PendingItem) {
  const statusDiff = feeStatusRank(left) - feeStatusRank(right);
  if (statusDiff !== 0) {
    return statusDiff;
  }
  return right.sortTime.localeCompare(left.sortTime);
}

function pendingAppointmentSortTime(item: Appointment) {
  const startTime = normalizeStartTime(item.startTime) || '00:00';
  return item.visitDate ? `${item.visitDate}T${startTime}:00` : item.createdAt || '';
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

function openPendingPayment(item: FinancialRecord) {
  if (!isPendingFinancialRecord(item)) return;
  uni.navigateTo({ url: '/pages/pending-payments/index' });
}

function onPaymentTouchStart(event: TouchEvent) {
  paymentTouchStartX = event.changedTouches?.[0]?.clientX ?? 0;
}

function onPaymentTouchEnd(event: TouchEvent) {
  const endX = event.changedTouches?.[0]?.clientX ?? paymentTouchStartX;
  const deltaX = endX - paymentTouchStartX;
  if (Math.abs(deltaX) < 60) return;
  activePaymentTab.value = deltaX < 0 ? 'paid' : 'unpaid';
}

onLoad((options) => {
  mode.value = options?.mode === 'record' ? 'record' : 'pending';
  activePaymentTab.value = 'unpaid';
  uni.setNavigationBarTitle({ title: mode.value === 'record' ? '缴费退费记录' : '门诊缴费' });
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
    request<Appointment[]>({ url: `/appointments?${patientQuery}`, method: 'GET' }),
    request<PaymentOrder[]>({ url: `/payments?businessType=APPOINTMENT&${patientQuery}`, method: 'GET' }),
    request<MedicalOrder[]>({ url: `/medical-orders?${patientQuery}&view=PAYMENT_RECORD`, method: 'GET' }),
    request<Prescription[]>({ url: `/prescriptions?${patientQuery}&view=PAYMENT_RECORD`, method: 'GET' })
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
    .filter(isVisibleAppointmentPayment)
    .map((item) => {
      const payable = isPayableAppointment(item);
      const amount = paymentByBusinessId.get(item.id)?.amount ?? 0.01;
      return {
      businessType: 'APPOINTMENT' as const,
      businessId: item.id,
      feeType: 'REGISTRATION' as const,
      title: `${item.departmentName} · ${item.doctorName}`,
      description: `${item.visitDate} ${item.period}`,
      note: feePaymentNote(payable),
      payable,
      amount,
      insuranceAmount: insuranceAmount({
        feeType: 'REGISTRATION',
        amount
      }),
      sortTime: pendingAppointmentSortTime(item)
    };
    })
    .sort(sortFeeItems);

  medicalOrderItems.value = medicalOrders
    .filter(isVisibleMedicalOrderPayment)
    .map((item) => {
      const payable = isPayableMedicalOrder(item);
      return {
      businessType: 'MEDICAL_ORDER' as const,
      businessId: item.id,
      feeType: item.orderType,
      title: item.itemName,
      description: `${orderTypeLabel(item.orderType)} · ${urgencyLabel(item.urgency)}`,
      note: feePaymentNote(payable),
      payable,
      amount: item.amount,
      insuranceAmount: insuranceAmount({ feeType: item.orderType, amount: item.amount }),
      sortTime: item.createdAt || ''
    };
    })
    .sort(sortFeeItems);

  prescriptionItems.value = prescriptions
    .filter(isVisiblePrescriptionPayment)
    .map((item) => {
      const payable = isPayablePrescription(item);
      return {
      businessType: 'PRESCRIPTION' as const,
      businessId: item.id,
      feeType: 'DRUG' as const,
      title: '药费',
      description: item.diagnosis || '待医生确认诊断',
      note: feePaymentNote(payable),
      payable,
      amount: item.totalAmount,
      insuranceAmount: insuranceAmount({ feeType: 'DRUG', amount: item.totalAmount }),
      sortTime: item.confirmedAt || item.createdAt || ''
    };
    })
    .sort(sortFeeItems);

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
    uni.showLoading({ title: '处理中…', mask: true });
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
    uni.hideLoading();
    uni.showToast({ title: method === 'MEDICAL_INSURANCE' ? '医保缴费成功' : '缴费成功', icon: 'success' });
    closePaymentDialog();
    await load();
  } catch (error) {
    uni.hideLoading();
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

.payment-tabs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8rpx;
  margin-bottom: 20rpx;
  padding: 8rpx;
  border: 1px solid #cdeff0;
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.76);
  box-shadow: 0 8rpx 22rpx rgba(80, 100, 95, 0.06);
}

.payment-tab {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  min-width: 0;
  height: 72rpx;
  border-radius: 14rpx;
  color: #64748b;
  font-size: 28rpx;
  font-weight: 700;
}

.payment-tab.active {
  background: linear-gradient(135deg, #0cbdcc 0%, #0899a5 100%);
  color: #fff;
  box-shadow: 0 8rpx 18rpx rgba(8, 153, 165, 0.22);
}

.payment-tab em {
  font-style: normal;
  font-size: 22rpx;
  font-weight: 700;
  opacity: 0.86;
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
  align-items: center;
  gap: 14rpx;
  margin-bottom: 20rpx;
}

.section-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48rpx;
  height: 48rpx;
  border-radius: 14rpx;
  font-size: 26rpx;
  font-weight: 800;
  color: #fff;
  flex-shrink: 0;
}

.reg-icon { background: linear-gradient(135deg, #0cbdcc 0%, #0899a5 100%); }
.check-icon { background: linear-gradient(135deg, #F0A860 0%, #E08840 100%); }
.lab-icon { background: linear-gradient(135deg, #8B5CF6 0%, #7C3AED 100%); }
.disposal-icon { background: linear-gradient(135deg, #E88870 0%, #D06050 100%); }
.drug-icon { background: linear-gradient(135deg, #5CBF98 0%, #3DA878 100%); }

.section-count {
  margin-left: auto;
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
  background: #f1f5f9;
  color: #64748b;
  font-size: 22rpx;
  font-weight: 600;
}

.section-title {
  color: #0f172a;
  font-size: 30rpx;
  font-weight: 700;
}

.fee-card {
  border-left: 6rpx solid transparent;
  overflow: hidden;
}

.fee-registration { border-left-color: #0899a5; }
.fee-check { border-left-color: #E08840; }
.fee-lab { border-left-color: #7C3AED; }
.fee-disposal { border-left-color: #D06050; }
.fee-drug { border-left-color: #3DA878; }

.fee-status-badge {
  display: inline-block;
  align-self: flex-start;
  margin-top: 6rpx;
  padding: 4rpx 18rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 600;
  line-height: 1.6;
}

.pending-badge {
  background: linear-gradient(135deg, #FF6B6B 0%, #EE5A24 100%);
  color: #fff;
  box-shadow: 0 4rpx 12rpx rgba(238, 90, 36, 0.35);
  animation: badge-pulse 2s ease-in-out infinite;
}

.paid-badge {
  background: #dcfce7;
  color: #166534;
}

@keyframes badge-pulse {
  0%, 100% { box-shadow: 0 4rpx 12rpx rgba(238, 90, 36, 0.35); }
  50% { box-shadow: 0 4rpx 20rpx rgba(238, 90, 36, 0.55); }
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

.category-name {
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

import { http } from './http';

export type BusinessType = 'APPOINTMENT' | 'MEDICAL_ORDER' | 'PRESCRIPTION';
export type PaymentChannel = 'WECHAT' | 'ALIPAY' | 'SIMULATED';

export interface PaymentOrder {
  id: string;
  businessType: BusinessType;
  businessId: string;
  patientId: string;
  amount: number;
  paymentMethod: string;
  status: 'PENDING' | 'PAID' | 'FAILED' | 'CANCELLED' | 'REFUNDED' | string;
  operatorId?: string;
  paidAt?: string;
  createdAt?: string;
  paymentScene?: string;
  channelTradeNo?: string;
}

export interface RefundOrder {
  id: string;
  businessType: BusinessType;
  businessId: string;
  patientId: string;
  amount: number;
  reason: string;
  status: string;
  operatorId?: string;
  refundedAt?: string;
}

export async function getPayments(params: {
  patientId?: string;
  businessId?: string;
  businessType?: BusinessType;
  status?: string;
} = {}) {
  return (await http.get<PaymentOrder[]>('/payments', { params })).data;
}

export async function getRefunds(params: { patientId?: string; businessId?: string } = {}) {
  return (await http.get<RefundOrder[]>('/refunds', { params })).data;
}

export async function createPaymentOrder(payload: {
  businessType: BusinessType;
  businessId: string;
  patientId: string;
  amount: number;
  paymentMethod: string;
}) {
  return (await http.post<PaymentOrder>('/payments/orders', payload)).data;
}

export async function refundDrugReturn(payload: {
  returnId: string;
  prescriptionId: string;
  patientId: string;
  amount: number;
  reason?: string;
}) {
  return (await http.post<RefundOrder>('/refunds/drug-return', payload)).data;
}

export async function confirmTestPayment(payload: {
  businessType: BusinessType;
  businessId: string;
  patientId: string;
  channel: PaymentChannel;
  channelTradeNo: string;
}) {
  return (await http.post<PaymentOrder>('/payments/test-callback', payload)).data;
}

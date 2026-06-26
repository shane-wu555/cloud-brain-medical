import type { Appointment } from '../api/appointment';

const REGISTERED_STATUSES = new Set(['WAITING', 'CALLED', 'IN_VISIT', 'REVISIT_WAITING']);

export function appointmentStatusLabel(appointment: Pick<Appointment, 'status' | 'paymentStatus'>) {
  if (appointment.paymentStatus === 'REFUNDED') return '已退号';
  if (appointment.status === 'PENDING_PAYMENT') return '待支付';
  if (REGISTERED_STATUSES.has(appointment.status)) return '已挂号';
  if (appointment.status === 'CANCELLED') return '已取消';
  if (appointment.status === 'FINISHED') return '已完成';
  return appointment.status;
}

export function paymentStatusLabel(status: string) {
  return {
    UNPAID: '待支付',
    PAID: '已支付',
    FAILED: '支付失败',
    CANCELLED: '已取消',
    REFUNDED: '已退款'
  }[status] ?? status;
}

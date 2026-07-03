import type { Appointment } from '../api/appointment';

export function appointmentStatusLabel(appointment: Pick<Appointment, 'status' | 'paymentStatus'>) {
  if (appointment.paymentStatus === 'REFUNDED') return '已退号';
  if (appointment.status === 'PENDING_PAYMENT') return '待支付';
  if (appointment.status === 'WAITING') return '待就诊';
  if (appointment.status === 'CALLED') return '已叫号';
  if (appointment.status === 'IN_VISIT') return '就诊中';
  if (appointment.status === 'REVISIT_WAITING') return '复诊待诊';
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

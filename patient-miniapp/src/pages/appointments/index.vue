<template>
  <view class="page">
    <view class="card">
      <view class="title">我的挂号</view>
      <view v-for="item in appointments" :key="item.id" class="row">
        <text>{{ item.departmentName }} · {{ item.doctorName }}</text>
        <text class="muted">{{ item.businessNo }} · {{ item.visitDate }} · {{ appointmentStatusLabel(item) }}</text>
        <view class="actions">
          <button v-if="canCancel(item)" size="mini" @click="cancel(item)">{{ cancelLabel(item) }}</button>
          <button v-if="canRevisit(item)" size="mini" class="btn-revisit" @click="revisit(item)">复诊报到</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { request } from '../../api/http';
import { useAuthStore } from '../../stores/auth';

interface Appointment {
  id: string;
  businessNo: string;
  departmentName: string;
  doctorName: string;
  visitDate: string;
  status: string;
  paymentStatus?: string;
}

const auth = useAuthStore();
const appointments = ref<Appointment[]>([]);

function todayStr() {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function appointmentStatusLabel(item: Appointment) {
  if (item.paymentStatus === 'REFUNDED') return '已退号';
  if (item.status === 'PENDING_PAYMENT') return '待支付';
  if (['WAITING', 'CALLED', 'IN_VISIT'].includes(item.status)) return '已挂号';
  if (item.status === 'REVISIT_WAITING') return '复诊候诊中';
  if (item.status === 'CANCELLED') return '已取消';
  if (item.status === 'FINISHED') return '已完成';
  return item.status;
}

function canCancel(item: Appointment) {
  return item.visitDate > todayStr() && !['CANCELLED', 'FINISHED'].includes(item.status);
}

function canRevisit(item: Appointment) {
  return item.status === 'FINISHED' && item.visitDate === todayStr();
}

function cancelLabel(item: Appointment) {
  return item.paymentStatus === 'PAID' ? '取消并退费' : '取消';
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
  appointments.value = await request<Appointment[]>({ url: `/appointments?patientId=${patient.id}`, method: 'GET' });
});

async function cancel(item: Appointment) {
  try {
    const patient = auth.requireBoundPatient();
    await request({ url: `/appointments/${item.id}/cancel`, method: 'POST' });
    uni.showToast({
      title: item.paymentStatus === 'PAID' ? '已取消，退款处理中' : '已取消',
      icon: 'none',
      duration: 2200
    });
    appointments.value = await request<Appointment[]>({ url: `/appointments?patientId=${patient.id}`, method: 'GET' });
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' });
  }
}

async function revisit(item: Appointment) {
  try {
    await request({ url: `/appointments/${item.id}/revisit`, method: 'POST' });
    uni.showToast({ title: '已加入复诊队列，请等候叫号', icon: 'none', duration: 2500 });
    const patient = auth.requireBoundPatient();
    appointments.value = await request<Appointment[]>({ url: `/appointments?patientId=${patient.id}`, method: 'GET' });
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' });
  }
}
</script>

<style scoped>
.row { display: flex; flex-direction: column; gap: 8rpx; padding: 20rpx 0; border-bottom: 1px solid #eee; }
.actions { display: flex; gap: 16rpx; margin-top: 8rpx; }
.btn-revisit { background: #1565c0 !important; color: #fff !important; }
</style>

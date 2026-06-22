<template>
  <view class="page">
    <view class="card">
      <view class="title">选择科室</view>
      <picker :range="departments" range-key="name" @change="selectDepartment">
        <view class="input">{{ selectedDepartment?.name ?? '请选择科室' }}</view>
      </picker>
    </view>
    <view v-for="schedule in schedules" :key="schedule.id" class="card schedule">
      <view><text class="doctor">{{ schedule.doctorName }}</text> · {{ schedule.workDate }} {{ schedule.period }}</view>
      <view class="muted">剩余号源：{{ schedule.capacity - schedule.booked }}</view>
      <button class="button" :disabled="schedule.booked >= schedule.capacity" @click="book(schedule)">锁号并支付</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { request } from '../../api/http';
import { useAuthStore } from '../../stores/auth';

interface Department { id: string; name: string }
interface Schedule { id: string; doctorId: string; doctorName: string; departmentId: string; workDate: string; period: string; capacity: number; booked: number }
interface Appointment { id: string }
const auth = useAuthStore();
const departments = ref<Department[]>([]);
const selectedDepartmentId = ref('');
const schedules = ref<Schedule[]>([]);
const selectedDepartment = computed(() => departments.value.find(item => item.id === selectedDepartmentId.value));

async function loadSchedules() {
  schedules.value = await request<Schedule[]>({ url: `/schedules?departmentId=${selectedDepartmentId.value}`, method: 'GET' });
}
async function selectDepartment(event: { detail: { value: string } }) {
  selectedDepartmentId.value = departments.value[Number(event.detail.value)]?.id ?? '';
  await loadSchedules();
}
async function book(schedule: Schedule) {
  if (!auth.user?.realNameVerified) {
    uni.showToast({ title: '请先完成实名认证', icon: 'none' });
    return;
  }
  try {
    const appointment = await request<Appointment>({
      url: '/appointments', method: 'POST', data: {
        scheduleId: schedule.id, patientId: auth.user.id, patientName: auth.user.name,
        doctorId: schedule.doctorId, doctorName: schedule.doctorName,
        departmentId: schedule.departmentId, departmentName: selectedDepartment.value?.name,
        visitDate: schedule.workDate, period: schedule.period, riskLevel: 'LOW'
      }
    });
    await request({ url: `/appointments/${appointment.id}/pay`, method: 'POST', data: { paymentMethod: 'WECHAT', amount: 0, operatorId: auth.user.id } });
    uni.showToast({ title: '挂号成功', icon: 'success' });
    await loadSchedules();
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' });
  }
}
onMounted(async () => {
  departments.value = await request<Department[]>({ url: '/departments', method: 'GET' });
  selectedDepartmentId.value = departments.value[0]?.id ?? '';
  if (selectedDepartmentId.value) await loadSchedules();
});
</script>

<style scoped>.schedule { display: flex; flex-direction: column; gap: 12rpx; }.doctor { font-weight: 700; font-size: 32rpx; }</style>

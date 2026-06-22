<template><view class="page"><view class="card"><view class="title">我的挂号</view><view v-for="item in appointments" :key="item.id" class="row"><text>{{ item.departmentName }} · {{ item.doctorName }}</text><text class="muted">{{ item.visitDate }} {{ item.status }}</text></view></view></view></template>
<script setup lang="ts">
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { request } from '../../api/http';
import { useAuthStore } from '../../stores/auth';
interface Appointment { id: string; departmentName: string; doctorName: string; visitDate: string; status: string }
const auth = useAuthStore(); const appointments = ref<Appointment[]>([]);
onShow(async () => { appointments.value = await request<Appointment[]>({ url: `/appointments?patientId=${auth.user?.id ?? ''}`, method: 'GET' }); });
</script>
<style scoped>.row { display: flex; flex-direction: column; gap: 8rpx; padding: 20rpx 0; border-bottom: 1px solid #eee; }</style>

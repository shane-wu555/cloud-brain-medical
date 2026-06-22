<template><view class="page"><view class="card"><view class="title">我的挂号</view><view v-for="item in appointments" :key="item.id" class="row"><text>{{ item.departmentName }} · {{ item.doctorName }}</text><text class="muted">{{ item.businessNo }} · {{ item.visitDate }} · {{ item.status }}</text><button v-if="canCancel(item)" size="mini" @click="cancel(item)">取消并退费</button></view></view></view></template>
<script setup lang="ts">
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { request } from '../../api/http';
import { useAuthStore } from '../../stores/auth';
interface Appointment { id: string; businessNo:string; departmentName: string; doctorName: string; visitDate: string; status: string }
const auth = useAuthStore(); const appointments = ref<Appointment[]>([]);
onShow(async () => { appointments.value = await request<Appointment[]>({ url: `/appointments?patientId=${auth.user?.id ?? ''}`, method: 'GET' }); });
function canCancel(item:Appointment){const today=new Date().toISOString().slice(0,10);return item.visitDate>today&&!['CANCELLED','FINISHED'].includes(item.status)}
async function cancel(item:Appointment){try{await request({url:`/appointments/${item.id}/cancel`,method:'POST'});uni.showToast({title:'已取消，退款处理中'});appointments.value=await request<Appointment[]>({url:'/appointments',method:'GET'})}catch(e){uni.showToast({title:(e as Error).message,icon:'none'})}}
</script>
<style scoped>.row { display: flex; flex-direction: column; gap: 8rpx; padding: 20rpx 0; border-bottom: 1px solid #eee; }</style>

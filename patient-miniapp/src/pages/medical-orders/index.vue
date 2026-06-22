<template>
  <view class="page">
    <view class="tabs"><button size="mini" @click="tab='orders'">医技申请</button><button size="mini" @click="tab='reports'">正式报告</button></view>
    <template v-if="tab==='orders'">
      <view v-for="order in orders" :key="order.id" class="card row">
        <view class="title">{{ order.projectName }}</view>
        <view class="muted">{{ labels[order.orderType] }} · {{ order.urgency==='EMERGENCY'?'急诊':'常规' }} · ¥{{ order.amount }}</view>
        <view>状态：{{ order.status }}<text v-if="order.executorName"> · {{ order.executorName }} / {{ order.executionLocation }}</text></view>
        <button v-if="order.paymentStatus==='UNPAID'" class="button" @click="pay(order)">微信模拟缴费 ¥{{ order.amount }}</button>
      </view>
      <view v-if="!orders.length" class="card muted">暂无医技申请</view>
    </template>
    <template v-else>
      <view v-for="report in reports" :key="report.id" class="card row">
        <view class="title">{{ labels[report.reportType] }}正式报告</view>
        <view><b>所见/过程：</b>{{ report.findings || '—' }}</view>
        <view><b>结论：</b>{{ report.conclusion }}</view>
        <view><b>建议：</b>{{ report.advice || '—' }}</view>
        <view class="muted">{{ report.confirmedAt }} · 已由医生确认</view>
      </view>
      <view v-if="!reports.length" class="card muted">暂无已确认报告</view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app';
import { ref } from 'vue';
import { request } from '../../api/http';
import { useAuthStore } from '../../stores/auth';
interface Order{id:string;orderType:'CHECK'|'LAB'|'DISPOSAL';projectName:string;amount:number;urgency:string;paymentStatus:string;status:string;executorName?:string;executionLocation?:string}
interface Report{id:string;reportType:'CHECK'|'LAB'|'DISPOSAL';findings:string;conclusion:string;advice:string;confirmedAt:string}
const auth=useAuthStore();const tab=ref<'orders'|'reports'>('orders');const orders=ref<Order[]>([]);const reports=ref<Report[]>([]);
const labels={CHECK:'检查',LAB:'检验',DISPOSAL:'处置'};
async function load(){orders.value=await request<Order[]>({url:'/medical-orders',method:'GET'});reports.value=await request<Report[]>({url:'/medical-orders/reports',method:'GET'});}
async function pay(order:Order){if(!auth.user)return;try{await request({url:'/payments/orders',method:'POST',data:{businessType:'MEDICAL_ORDER',businessId:order.id,patientId:auth.user.id,amount:order.amount,paymentMethod:'WECHAT_TEST'}});await request({url:'/payments/test-callback',method:'POST',data:{businessType:'MEDICAL_ORDER',businessId:order.id,patientId:auth.user.id,channel:'WECHAT',channelTradeNo:`wx-medical-${order.id}-${Date.now()}`}});uni.showToast({title:'缴费成功',icon:'success'});await load();}catch(error){uni.showToast({title:(error as Error).message,icon:'none'});}}
onShow(load);
</script>

<style scoped>.tabs{display:flex;gap:16rpx;margin-bottom:20rpx}.row{display:flex;flex-direction:column;gap:14rpx}.title{font-weight:700;font-size:32rpx}</style>

<template>
  <view class="page">
    <view class="card">
      <view class="title">检查检验报告</view>
      <view class="muted">这里只展示已确认的检查报告和检验报告，处置项目请到“处置安排”查看。</view>
    </view>

    <view v-for="report in reports" :key="report.id" class="card row">
      <view class="title-sm">{{ labels[report.reportType] }}正式报告</view>
      <view><text class="label">所见/过程：</text>{{ report.findings || '—' }}</view>
      <view><text class="label">结论：</text>{{ report.conclusion }}</view>
      <view><text class="label">建议：</text>{{ report.advice || '—' }}</view>
      <view class="muted">{{ report.confirmedAt }} · 已由医生确认</view>
    </view>

    <view v-if="!reports.length" class="card muted">暂无已确认报告</view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app';
import { ref } from 'vue';
import { request } from '../../api/http';

interface Report {
  id: string;
  reportType: 'CHECK' | 'LAB' | 'DISPOSAL';
  findings: string;
  conclusion: string;
  advice: string;
  confirmedAt: string;
}

const reports = ref<Report[]>([]);
const labels = { CHECK: '检查', LAB: '检验' } as const;

onShow(async () => {
  const response = await request<Report[]>({ url: '/medical-orders/reports', method: 'GET' });
  reports.value = response.filter((item) => item.reportType === 'CHECK' || item.reportType === 'LAB');
});
</script>

<style scoped>
.row {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.title-sm {
  font-size: 32rpx;
  font-weight: 700;
}

.label {
  color: #0f766e;
  font-weight: 600;
}
</style>

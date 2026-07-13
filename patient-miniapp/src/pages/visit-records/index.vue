<template>
  <patient-nav-bar title="就诊记录" />
  <view class="page records-page">
    <view v-if="loading" class="loading-hint">加载中...</view>

    <block v-else-if="groupedRecords.length">
      <view v-for="group in groupedRecords" :key="group.label" class="record-group">
        <view class="group-label">{{ group.label }}</view>
        <view
          v-for="item in group.items"
          :key="item.id"
          class="card record-item"
          @tap="goDetail(item)"
        >
          <view class="record-header">
            <view class="record-dept">{{ item.departmentName }}</view>
            <view :class="['status-tag', item.status]">{{ statusLabel(item.status) }}</view>
          </view>
          <view class="record-meta">
            <text>{{ item.doctorName }}</text>
            <text class="record-date">{{ item.visitDate }}</text>
          </view>
          <view v-if="item.type" class="record-type-tag">{{ item.type }}</view>
        </view>
      </view>
    </block>

    <view v-else class="empty-hint">暂无就诊记录</view>
  </view>

  <patient-tab-bar current="records" />
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { request } from '../../api/http';
import { useAuthStore } from '../../stores/auth';

interface VisitRecord {
  id: string;
  appointmentId?: string;
  doctorName: string;
  departmentName: string;
  visitDate: string;
  status: string;
  type?: string;
}

const auth = useAuthStore();
const records = ref<VisitRecord[]>([]);
const loading = ref(true);

const groupedRecords = computed(() => {
  const groups: Record<string, VisitRecord[]> = {};
  for (const record of records.value) {
    const label = record.visitDate ? record.visitDate.slice(0, 7) : '';
    if (!label) continue;
    if (!groups[label]) {
      groups[label] = [];
    }
    groups[label].push(record);
  }
  return Object.entries(groups)
    .sort((a, b) => b[0].localeCompare(a[0]))
    .map(([label, items]) => {
      items.sort((a, b) => b.visitDate.localeCompare(a.visitDate));
      return { label, items };
    });
});

function statusLabel(status: string) {
  const map: Record<string, string> = {
    DRAFT: '就诊中',
    ACTIVE: '就诊中',
    ARCHIVED: '已就诊',
    COMPLETED: '已就诊',
    FINISHED: '已就诊',
    CANCELLED: '已取消',
    PENDING: '待就诊',
    PENDING_PAYMENT: '待缴费',
    WAITING: '待就诊',
    CONFIRMED: '待就诊',
    IN_PROGRESS: '就诊中',
    REFUNDED: '已退款',
    FAILED: '已失败',
    PAID: '已缴费',
    UNPAID: '未缴费'
  };
  return map[status] || status;
}

onShow(async () => {
  if (!auth.token) {
    uni.reLaunch({ url: '/pages/login/index' });
    return;
  }

  loading.value = true;
  try {
    await auth.loadProfile();
    const patient = auth.boundPatient;
    if (!patient) {
      records.value = [];
      uni.showToast({ title: '请先添加并绑定就诊人', icon: 'none', duration: 3000 });
      uni.navigateTo({ url: '/pages/real-name/index?prompt=needPatient' });
      return;
    }

    const [appointments, medicalRecords] = await Promise.all([
      request<any[]>({ url: `/appointments?patientId=${encodeURIComponent(patient.id)}`, method: 'GET' }).catch(() => []),
      request<any[]>({ url: `/medical-records?patientId=${encodeURIComponent(patient.id)}`, method: 'GET' }).catch(() => [])
    ]);

    const merged: VisitRecord[] = [
      ...(Array.isArray(appointments) ? appointments : [])
        .filter((item: any) => item.scheduledDate || item.appointmentDate)
        .map((item: any) => ({
          appointmentId: item.id || '',
          doctorName: item.doctorName || '',
          departmentName: item.departmentName || '',
          visitDate: item.scheduledDate || item.appointmentDate || '',
          status: item.status || 'PENDING',
          id: item.id || '',
          type: '挂号'
        })),
      ...(Array.isArray(medicalRecords) ? medicalRecords : [])
        .filter((item: any) => item.visitDate)
        .map((item: any) => ({
          appointmentId: item.appointmentId || '',
          doctorName: item.doctorName || '',
          departmentName: item.departmentName || '',
          visitDate: item.visitDate || '',
          status: item.status || 'ACTIVE',
          id: item.id || '',
          type: '就诊'
        }))
    ];

    records.value = merged.sort((a, b) => (b.visitDate || '').localeCompare(a.visitDate || ''));
  } catch (error: any) {
    uni.showToast({ title: error?.message || '加载失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
});

function goDetail(item: VisitRecord) {
  if (item.appointmentId) {
    uni.navigateTo({
      url: `/pages/medical-records/index?appointmentId=${encodeURIComponent(item.appointmentId)}`
    });
    return;
  }

  if (item.type === '就诊') {
    uni.navigateTo({ url: '/pages/medical-records/index' });
    return;
  }

  uni.navigateTo({ url: '/pages/appointments/index' });
}
</script>

<style scoped>
.records-page {
  padding-bottom: 140rpx;
}

.loading-hint,
.empty-hint {
  text-align: center;
  color: #9aa8ba;
  font-size: 28rpx;
  padding: 80rpx 0;
}

.record-group {
  margin-bottom: 28rpx;
}

.group-label {
  color: #718096;
  font-size: 26rpx;
  font-weight: 600;
  margin-bottom: 16rpx;
  padding-left: 6rpx;
}

.record-item {
  margin-bottom: 16rpx;
}

.record-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}

.record-dept {
  font-size: 30rpx;
  font-weight: 600;
  color: #143450;
}

.record-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #718096;
  font-size: 26rpx;
  margin-bottom: 8rpx;
}

.record-type-tag {
  display: inline-block;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  background: var(--patient-theme-soft);
  color: var(--patient-theme-strong);
  font-size: 22rpx;
  font-weight: 500;
}

.status-tag {
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
}

.status-tag.PENDING,
.status-tag.WAITING,
.status-tag.CONFIRMED {
  background: #fff7ed;
  color: #c2410c;
}

.status-tag.ACTIVE,
.status-tag.IN_PROGRESS,
.status-tag.DRAFT {
  background: #ecfeff;
  color: #0f766e;
}

.status-tag.COMPLETED,
.status-tag.ARCHIVED,
.status-tag.FINISHED {
  background: #f1f5f9;
  color: #475569;
}

.status-tag.CANCELLED {
  background: #fff5f5;
  color: #c53030;
}

</style>

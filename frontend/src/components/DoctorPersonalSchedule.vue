<template>
  <section class="personal-schedule">
    <div class="personal-schedule__head">
      <div>
        <strong>我的排班</strong>
        <span>{{ rangeLabel }}</span>
      </div>
      <el-button size="small" text :loading="loading" @click="loadSchedule">刷新</el-button>
    </div>

    <div v-if="loading" class="personal-schedule__empty">正在加载排班...</div>
    <div v-else-if="error" class="personal-schedule__empty personal-schedule__empty--error">{{ error }}</div>
    <div v-else-if="!items.length" class="personal-schedule__empty">近 7 天暂无排班</div>
    <div v-else class="personal-schedule__list">
      <div
        v-for="item in items"
        :key="item.id"
        class="personal-schedule__item"
        :class="{ 'personal-schedule__item--today': item.workDate === todayKey }"
      >
        <div class="personal-schedule__date">
          <strong>{{ dateLabel(item.workDate) }}</strong>
          <span>{{ weekdayLabel(item.workDate) }}</span>
        </div>
        <div class="personal-schedule__shift">
          <div>
            <strong>{{ item.period }}</strong>
            <span>{{ item.roomName || item.departmentName || '未指定诊室' }}</span>
          </div>
          <em>{{ item.booked }}/{{ item.capacity }}</em>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getSchedules, type Schedule } from '../api/doctor';
import { useAuthStore } from '../store/auth';

const auth = useAuthStore();
const loading = ref(false);
const error = ref('');
const schedules = ref<Schedule[]>([]);

const today = new Date();
const todayKey = toDateKey(today);
const rangeLabel = computed(() => `${dateLabel(todayKey)} - ${dateLabel(toDateKey(addDays(today, 6)))}`);
const items = computed(() => [...schedules.value].sort((a, b) => {
  const dateOrder = a.workDate.localeCompare(b.workDate);
  return dateOrder || periodOrder(a.period) - periodOrder(b.period);
}));

function currentDoctorId() {
  return auth.user?.employeeNo || auth.user?.username || auth.user?.id || '';
}

async function loadSchedule() {
  const doctorId = currentDoctorId();
  if (!doctorId) {
    error.value = '未识别当前账号';
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    schedules.value = await getSchedules({ doctorId, bookingWindowOnly: true });
  } catch (err: any) {
    error.value = err?.response?.data?.message ?? err?.message ?? '排班加载失败';
  } finally {
    loading.value = false;
  }
}

function addDays(date: Date, days: number) {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
}

function toDateKey(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function dateLabel(value: string) {
  const [, month, day] = value.split('-');
  return `${Number(month)}月${Number(day)}日`;
}

function weekdayLabel(value: string) {
  const date = new Date(`${value}T00:00:00`);
  return ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][date.getDay()];
}

function periodOrder(period: string) {
  if (period === '上午') return 1;
  if (period === '下午') return 2;
  if (period === '全天') return 3;
  return 4;
}

onMounted(loadSchedule);
</script>

<style scoped>
.personal-schedule {
  margin: 0 12px 10px;
  padding: 10px;
  border: 1px solid #dbe7f0;
  border-radius: 8px;
  background: #fff;
}

.personal-schedule__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.personal-schedule__head strong {
  display: block;
  color: #0f172a;
  font-size: 14px;
}

.personal-schedule__head span {
  display: block;
  margin-top: 2px;
  color: #94a3b8;
  font-size: 11px;
}

.personal-schedule__empty {
  padding: 14px 4px;
  color: #94a3b8;
  text-align: center;
  font-size: 12px;
}

.personal-schedule__empty--error {
  color: #dc2626;
}

.personal-schedule__list {
  display: grid;
  gap: 6px;
  max-height: 220px;
  overflow: auto;
}

.personal-schedule__item {
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr);
  gap: 8px;
  padding: 8px;
  border-radius: 6px;
  background: #f8fafc;
}

.personal-schedule__item--today {
  background: #e6f9fa;
}

.personal-schedule__date strong,
.personal-schedule__shift strong {
  display: block;
  color: #0f172a;
  font-size: 13px;
}

.personal-schedule__date span,
.personal-schedule__shift span {
  display: block;
  margin-top: 3px;
  color: #64748b;
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.personal-schedule__shift {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  gap: 8px;
}

.personal-schedule__shift div {
  min-width: 0;
}

.personal-schedule__shift em {
  flex-shrink: 0;
  color: #0899a5;
  font-style: normal;
  font-size: 12px;
  font-weight: 700;
}
</style>

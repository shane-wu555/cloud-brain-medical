<template>
  <section class="doctor-schedule-page">
    <div class="personal-center-layout">
      <aside class="personal-sidebar">
        <div class="personal-card">
          <div class="personal-avatar">{{ doctorAvatar }}</div>
          <strong>{{ doctorName }}</strong>
          <span>{{ doctorMeta }}</span>
        </div>
        <nav class="personal-nav">
          <button
            v-for="item in personalNavItems"
            :key="item.key"
            type="button"
            :class="['personal-nav__item', activePersonalTab === item.key && 'personal-nav__item--active']"
            @click="activePersonalTab = item.key"
          >
            <span>{{ item.icon }}</span>
            <strong>{{ item.label }}</strong>
          </button>
        </nav>
      </aside>

      <main class="personal-main">
        <div v-if="activePersonalTab === 'schedule'" class="schedule-shell">
      <div class="schedule-header">
        <div>
          <h1>我的排班</h1>
          <p>{{ rangeLabel }} · {{ doctorName }}</p>
        </div>
        <div class="schedule-header__actions">
          <el-button size="small" @click="moveWeek(-1)">上一周</el-button>
          <el-button size="small" @click="resetWeek">本周</el-button>
          <el-button size="small" @click="moveWeek(1)">下一周</el-button>
          <el-button size="small" :loading="loading" @click="loadSchedule">刷新</el-button>
        </div>
      </div>

      <div class="schedule-summary">
        <div class="summary-item">
          <span>本周班次</span>
          <strong>{{ totalShifts }}</strong>
        </div>
        <div v-if="isOutpatientDoctor" class="summary-item">
          <span>总号源</span>
          <strong>{{ totalCapacity }}</strong>
        </div>
        <div v-if="isOutpatientDoctor" class="summary-item">
          <span>已预约</span>
          <strong>{{ totalBooked }}</strong>
        </div>
        <div v-if="isOutpatientDoctor" class="summary-item">
          <span>可预约</span>
          <strong>{{ totalAvailable }}</strong>
        </div>
        <div v-if="!isOutpatientDoctor" class="summary-item">
          <span>值班天数</span>
          <strong>{{ dutyDays }}</strong>
        </div>
        <div v-if="!isOutpatientDoctor" class="summary-item">
          <span>上午班</span>
          <strong>{{ morningShiftCount }}</strong>
        </div>
        <div v-if="!isOutpatientDoctor" class="summary-item">
          <span>下午班</span>
          <strong>{{ afternoonShiftCount }}</strong>
        </div>
      </div>

      <section class="work-card schedule-visual-card">
        <div class="schedule-board-tools">
          <div>
            <strong>周排班</strong>
          </div>
          <div class="schedule-legend">
            <span><i class="legend-dot legend-dot--morning"></i>上午</span>
            <span><i class="legend-dot legend-dot--afternoon"></i>下午</span>
            <span><i class="legend-dot legend-dot--suspended"></i>停诊</span>
          </div>
        </div>

        <div v-if="loading" class="schedule-state">正在加载排班...</div>
        <div v-else-if="error" class="schedule-state schedule-state--error">{{ error }}</div>
        <div v-else class="schedule-board-wrap">
          <div class="schedule-board" :style="scheduleBoardStyle">
            <div class="schedule-board__corner">
              <span>人员</span>
              <strong>时间</strong>
            </div>
            <div
              v-for="day in weekDays"
              :key="day.key"
              class="schedule-board__day"
              :class="{ 'schedule-board__day--today': day.key === todayKey, 'schedule-board__day--weekend': day.weekend }"
            >
              <strong>{{ day.weekdayLabel }}</strong>
              <span>{{ day.dateLabel }}</span>
              <em>{{ day.weekend ? '休息日' : '工作日' }}</em>
              <div class="schedule-board__ticks">
                <span>08</span>
                <span>12</span>
                <span>16</span>
              </div>
            </div>

            <div class="schedule-board__person">
              <div class="doctor-avatar">{{ doctorAvatar }}</div>
              <div>
                <strong>{{ doctorName }}</strong>
                <em>{{ doctorMeta }}</em>
              </div>
            </div>

            <div
              v-for="day in weekDays"
              :key="`cell-${day.key}`"
              class="schedule-board__cell"
              :class="{ 'schedule-board__cell--today': day.key === todayKey, 'schedule-board__cell--weekend': day.weekend }"
            >
              <div v-for="period in periods" :key="period.key" class="schedule-board__period-slot">
                <div
                  v-if="scheduleCell(day.key, period.key)"
                  class="schedule-shift"
                  :class="shiftClasses(scheduleCell(day.key, period.key), period.key)"
                >
                  <div class="schedule-shift__head">
                    <strong>{{ period.label }}</strong>
                    <span>{{ statusLabel(scheduleCell(day.key, period.key)?.status) }}</span>
                  </div>
                  <p>{{ period.time }}</p>
                  <em>{{ scheduleCell(day.key, period.key)?.roomName || scheduleCell(day.key, period.key)?.departmentName || '未指定诊室' }}</em>
                  <small v-if="isOutpatientDoctor">
                    号源 {{ scheduleCell(day.key, period.key)?.booked }}/{{ scheduleCell(day.key, period.key)?.capacity }} · 可约 {{ scheduleCell(day.key, period.key)?.available }}
                  </small>
                  <small v-else>{{ statusLabel(scheduleCell(day.key, period.key)?.status) }} · {{ scheduleCell(day.key, period.key)?.departmentName || '本岗位' }}</small>
                </div>
                <span v-else class="schedule-board__empty">{{ period.label }}</span>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>

        <div v-else class="profile-shell">
          <!-- Profile banner -->
          <section class="profile-banner">
            <div class="profile-banner__bg"></div>
            <div class="profile-banner__content">
              <div class="profile-banner__avatar">{{ doctorAvatar }}</div>
              <div class="profile-banner__info">
                <h2>{{ auth.user?.name || '未设置姓名' }}</h2>
                <div class="profile-banner__tags">
                  <span class="profile-banner__tag">{{ roleLabel }}</span>
                  <span class="profile-banner__tag profile-banner__tag--dept">{{ doctorMeta }}</span>
                </div>
              </div>
            </div>
          </section>

          <!-- Profile detail cards -->
          <div class="profile-detail-grid">
            <div class="profile-detail-card">
              <div class="profile-detail-card__icon profile-detail-card__icon--id">工</div>
              <div class="profile-detail-card__body">
                <span>工号</span>
                <strong>{{ auth.user?.employeeNo || '-' }}</strong>
              </div>
            </div>
            <div class="profile-detail-card">
              <div class="profile-detail-card__icon profile-detail-card__icon--account">账</div>
              <div class="profile-detail-card__body">
                <span>账号</span>
                <strong>{{ auth.user?.username || '-' }}</strong>
              </div>
            </div>
          </div>

          <!-- Change password -->
          <section class="work-card profile-password-card">
            <h3>修改密码</h3>
            <el-form :model="pwdForm" label-position="top" class="pwd-form" @submit.prevent>
              <el-form-item label="原密码">
                <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="输入原密码" />
              </el-form-item>
              <el-form-item label="新密码">
                <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少8位，含字母和数字" />
              </el-form-item>
              <el-button type="primary" class="pwd-submit-btn" :loading="pwdLoading" @click="handleChangePassword">
                确认修改
              </el-button>
            </el-form>
          </section>
        </div>
      </main>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { changeMyPassword } from '../api/auth';
import { getSchedules, type Schedule } from '../api/doctor';
import { useAuthStore } from '../store/auth';

const auth = useAuthStore();
const loading = ref(false);
const error = ref('');
const schedules = ref<Schedule[]>([]);
const weekOffset = ref(0);
const activePersonalTab = ref<'schedule' | 'profile'>('schedule');
const personalNavItems = [
  { key: 'schedule', label: '个人排班', icon: '日' },
  { key: 'profile', label: '个人信息', icon: '人' }
] as const;

const today = new Date();
const todayKey = toDateKey(today);
const periods = [
  { key: 'morning', label: '上午', time: '08:00 - 12:00' },
  { key: 'afternoon', label: '下午', time: '14:00 - 17:30' }
];

const weekStart = computed(() => addDays(today, weekOffset.value * 7));
const weekDays = computed(() => Array.from({ length: 7 }, (_, index) => {
  const date = addDays(weekStart.value, index);
  const key = toDateKey(date);
  return {
    key,
    dateLabel: dateLabel(key),
    weekdayLabel: weekdayLabel(key),
    weekend: date.getDay() === 0 || date.getDay() === 6
  };
}));

const rangeLabel = computed(() => {
  const first = weekDays.value[0]?.key ?? todayKey;
  const last = weekDays.value[weekDays.value.length - 1]?.key ?? todayKey;
  return `${dateLabel(first)} - ${dateLabel(last)}`;
});
const doctorName = computed(() => auth.user?.name || '当前医生');
const doctorAvatar = computed(() => doctorName.value.slice(-1));
const doctorMeta = computed(() => {
  const first = schedules.value[0];
  return first?.departmentName || first?.doctorId || auth.user?.employeeNo || auth.user?.username || '个人排班';
});
const roleLabel = computed(() => ({
  OUTPATIENT_DOCTOR: '门诊医生',
  CHECK_DOCTOR: '检查医生',
  LAB_DOCTOR: '检验医生',
  DISPOSAL_DOCTOR: '处置医生',
  PHARMACY_STAFF: '药房人员',
  CASHIER: '收费员',
  ADMIN: '管理员'
} as Record<string, string>)[auth.user?.role || ''] || auth.user?.role || '-');
const isOutpatientDoctor = computed(() => auth.user?.role === 'OUTPATIENT_DOCTOR');

const weekDateKeys = computed(() => new Set(weekDays.value.map((day) => day.key)));
const weeklySchedules = computed(() => schedules.value.filter((item) => weekDateKeys.value.has(item.workDate)));
const totalShifts = computed(() => weeklySchedules.value.length);
const totalCapacity = computed(() => weeklySchedules.value.reduce((sum, item) => sum + (item.capacity || 0), 0));
const totalBooked = computed(() => weeklySchedules.value.reduce((sum, item) => sum + (item.booked || 0), 0));
const totalAvailable = computed(() => weeklySchedules.value.reduce((sum, item) => sum + (item.available || 0), 0));
const dutyDays = computed(() => new Set(weeklySchedules.value.map((item) => item.workDate)).size);
const morningShiftCount = computed(() => weeklySchedules.value.filter((item) => periodKey(item.period) === 'morning').length);
const afternoonShiftCount = computed(() => weeklySchedules.value.filter((item) => periodKey(item.period) === 'afternoon').length);
const scheduleBoardStyle = computed(() => ({
  gridTemplateColumns: `180px repeat(${weekDays.value.length}, minmax(146px, 1fr))`
}));

const scheduleMap = computed(() => {
  const map = new Map<string, Schedule>();
  schedules.value.forEach((schedule) => {
    map.set(`${schedule.workDate}:${periodKey(schedule.period)}`, schedule);
  });
  return map;
});

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
    schedules.value = await getSchedules({ doctorId });
  } catch (err: any) {
    error.value = err?.response?.data?.message ?? err?.message ?? '排班加载失败';
  } finally {
    loading.value = false;
  }
}

function scheduleCell(date: string, period: string) {
  return scheduleMap.value.get(`${date}:${period}`);
}

function dayShiftCount(date: string) {
  return periods.filter((period) => scheduleCell(date, period.key)).length;
}

function moveWeek(delta: number) {
  weekOffset.value += delta;
}

function resetWeek() {
  weekOffset.value = 0;
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

function periodKey(period: string) {
  const value = (period || '').toUpperCase();
  if (value.includes('上午') || value.includes('MORNING')) return 'morning';
  if (value.includes('下午') || value.includes('AFTERNOON')) return 'afternoon';
  return value.toLowerCase();
}

function statusLabel(status?: string) {
  const labels = isOutpatientDoctor.value ? {
    ACTIVE: '可预约',
    SUSPENDED: '已停诊',
    FULL: '已约满'
  } : {
    ACTIVE: '正常排班',
    SUSPENDED: '已暂停',
    FULL: '已排满'
  };
  return (labels as Record<string, string>)[status || ''] ?? (status || (isOutpatientDoctor.value ? '可预约' : '正常排班'));
}

function shiftClasses(schedule: Schedule | undefined, period: string) {
  return [
    `schedule-shift--${period}`,
    schedule?.status === 'SUSPENDED' ? 'schedule-shift--suspended' : '',
    schedule?.status === 'FULL' ? 'schedule-shift--full-status' : ''
  ];
}

const pwdLoading = ref(false);
const pwdForm = reactive({
  oldPassword: '',
  newPassword: ''
});

async function handleChangePassword() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    ElMessage.warning('请填写原密码和新密码');
    return;
  }
  if (pwdForm.newPassword.length < 8) {
    ElMessage.warning('新密码至少8位');
    return;
  }
  if (!/[A-Za-z]/.test(pwdForm.newPassword) || !/\d/.test(pwdForm.newPassword)) {
    ElMessage.warning('新密码需包含字母和数字');
    return;
  }
  pwdLoading.value = true;
  try {
    await changeMyPassword(pwdForm.oldPassword, pwdForm.newPassword);
    ElMessage.success('密码已修改，请妥善保管');
    pwdForm.oldPassword = '';
    pwdForm.newPassword = '';
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || '修改失败';
    ElMessage.error(msg);
  } finally {
    pwdLoading.value = false;
  }
}

onMounted(loadSchedule);
</script>

<style scoped>
.doctor-schedule-page {
  flex: 1;
  min-height: 0;
  height: 100%;
  overflow: auto;
  padding: 0;
  background: transparent;
  box-sizing: border-box;
}

.personal-center-layout {
  height: 100%;
  display: grid;
  grid-template-columns: 236px minmax(0, 1fr);
  gap: 16px;
  min-height: 0;
}

.personal-sidebar {
  min-height: 0;
  padding: 14px;
  border: 1px solid #d0e8eb;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 1px 6px rgba(12,189,204,.06);
  box-sizing: border-box;
}

.personal-card {
  padding: 14px 4px 18px;
  border-bottom: 1px solid #e8f4f6;
  text-align: center;
}

.personal-card .personal-avatar {
  width: 60px;
  height: 60px;
  margin: 0 auto 12px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #0cbdcc, #0899a5);
  color: #fff;
  font-size: 22px;
  font-weight: 800;
  box-shadow: 0 3px 12px rgba(12,189,204,.25);
}

.personal-card strong,
.personal-card span {
  display: block;
}

.personal-card strong {
  color: #0e7b85;
  font-size: 16px;
}

.personal-card span {
  margin-top: 5px;
  color: #64748b;
  font-size: 12px;
  overflow-wrap: anywhere;
}

.personal-nav {
  display: grid;
  gap: 6px;
  padding-top: 14px;
}

.personal-nav__item {
  height: 44px;
  border: 1px solid transparent;
  border-radius: 8px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  background: transparent;
  color: #475569;
  cursor: pointer;
  text-align: left;
  transition: all .15s ease;
}

.personal-nav__item span {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 7px;
  background: #f0f8f9;
  color: #0899a5;
  font-size: 13px;
  font-weight: 700;
  transition: all .15s ease;
}

.personal-nav__item strong {
  font-size: 14px;
}

.personal-nav__item:hover,
.personal-nav__item--active {
  border-color: #a8e8ec;
  background: linear-gradient(135deg, #f0f8f9 0%, #e6f9fa 100%);
  color: #0e7b85;
}

.personal-nav__item--active span {
  background: linear-gradient(135deg, #0cbdcc, #0899a5);
  color: #fff;
}

.personal-main {
  min-width: 0;
  min-height: 0;
  overflow: auto;
}

.schedule-shell {
  max-width: 1480px;
  margin: 0 auto;
}

.profile-shell {
  max-width: 980px;
  margin: 0 auto;
}

/* ── Profile Banner ── */
.profile-banner {
  position: relative;
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 20px;
  background: #fff;
  box-shadow: 0 2px 12px rgba(12,189,204,.10);
}
.profile-banner__bg {
  height: 100px;
  background: linear-gradient(135deg, #0cbdcc 0%, #0899a5 50%, #0e7b85 100%);
}
.profile-banner__content {
  display: flex;
  align-items: flex-end;
  gap: 18px;
  padding: 0 24px 20px;
  margin-top: -36px;
  position: relative;
  z-index: 1;
}
.profile-banner__avatar {
  width: 72px; height: 72px;
  border-radius: 50%;
  background: linear-gradient(135deg, #0cbdcc, #0899a5);
  color: #fff;
  font-size: 28px; font-weight: 800;
  display: flex; align-items: center; justify-content: center;
  border: 4px solid #fff;
  box-shadow: 0 4px 16px rgba(12,189,204,.30);
  flex-shrink: 0;
}
.profile-banner__info h2 {
  margin: 0 0 8px;
  font-size: 22px; font-weight: 700;
  color: #0e7b85;
}
.profile-banner__tags {
  display: flex; gap: 8px; flex-wrap: wrap;
}
.profile-banner__tag {
  padding: 3px 12px;
  border-radius: 12px;
  font-size: 12px; font-weight: 600;
  background: linear-gradient(135deg, #0cbdcc, #0899a5);
  color: #fff;
}
.profile-banner__tag--dept {
  background: #e6f9fa;
  color: #0899a5;
}

/* ── Profile Detail Cards ── */
.profile-detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}
.profile-detail-card {
  display: flex;
  align-items: center; gap: 14px;
  padding: 18px 20px;
  border: 1px solid #d0e8eb;
  border-radius: 10px;
  background: linear-gradient(135deg, #f9fcfd 0%, #fff 60%);
  box-shadow: 0 1px 4px rgba(12,189,204,.06);
  transition: box-shadow .15s ease, transform .15s ease;
}
.profile-detail-card:hover {
  box-shadow: 0 4px 16px rgba(12,189,204,.12);
  transform: translateY(-1px);
}
.profile-detail-card__icon {
  width: 44px; height: 44px;
  border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  font-size: 16px; font-weight: 800;
  flex-shrink: 0;
}
.profile-detail-card__icon--id      { background: #e6f9fa; color: #0899a5; }
.profile-detail-card__icon--account { background: #eff6ff; color: #3b82f6; }
.profile-detail-card__body span {
  display: block;
  font-size: 12px; color: #9ca3af; font-weight: 500;
  text-transform: uppercase; letter-spacing: .3px; margin-bottom: 4px;
}
.profile-detail-card__body strong {
  font-size: 15px; color: #374151; font-weight: 600;
}

/* ── Change password ── */
.profile-password-card {
  margin-top: 20px;
  padding: 24px;
}
.profile-password-card h3 {
  margin: 0 0 18px;
  font-size: 16px; font-weight: 700;
  color: #0e7b85;
}
.pwd-form :deep(.el-form-item__label) {
  font-size: 13px; color: #64748b;
}
.pwd-submit-btn {
  width: 100%;
  height: 42px;
  border-radius: 10px;
  font-size: 15px; font-weight: 600;
  background: linear-gradient(135deg, #0cbdcc 0%, #0899a5 100%);
  border: none;
  box-shadow: 0 3px 12px rgba(12,189,204,.30);
  margin-top: 4px;
}
.pwd-submit-btn:hover {
  box-shadow: 0 5px 18px rgba(12,189,204,.40);
  opacity: .95;
}

.schedule-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.schedule-header__eyebrow {
  display: block;
  margin-bottom: 5px;
  color: #0cbdcc;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: .5px;
}

.schedule-header h1 {
  margin: 0;
  color: #0e7b85;
  font-size: 24px;
  font-weight: 750;
  letter-spacing: 0;
}

.schedule-header p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
}

.schedule-header__actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.schedule-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(140px, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.summary-item {
  min-height: 72px;
  padding: 14px 16px;
  border: 1px solid #d0e8eb;
  border-radius: 10px;
  background: linear-gradient(180deg, #f9fcfd 0%, #fff 100%);
  box-shadow: 0 1px 4px rgba(12,189,204,.08);
  box-sizing: border-box;
}

.summary-item span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.summary-item strong {
  display: block;
  margin-top: 7px;
  color: #0e7b85;
  font-size: 24px;
  line-height: 1;
}

.work-card {
  border: 1px solid #d0e8eb;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 1px 6px rgba(12,189,204,.08);
}

.schedule-visual-card {
  overflow: hidden;
}

.schedule-board-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border-bottom: 1px solid #e8f4f6;
  background: linear-gradient(180deg, #f9fcfd 0%, #f0f8f9 100%);
}

.schedule-board-tools strong,
.schedule-board-tools span {
  display: block;
}

.schedule-board-tools strong {
  color: #0e7b85;
  font-size: 15px;
}

.schedule-board-tools > div > span {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.schedule-legend {
  display: flex;
  align-items: center;
  gap: 14px;
  color: #475569;
  font-size: 12px;
  white-space: nowrap;
}

.schedule-legend span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  display: inline-block;
}

.legend-dot--morning {
  background: #ddf7dc;
  border-left: 3px solid #4aa564;
}
.legend-dot--afternoon {
  background: #d9f1fb;
  border-left: 3px solid #2f91b4;
}
.legend-dot--suspended {
  background: #e5e7eb;
  border-left: 3px solid #94a3b8;
}

.schedule-state {
  min-height: 360px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #64748b;
  font-size: 14px;
}

.schedule-state--error {
  color: #dc2626;
}

.schedule-board-wrap {
  overflow: auto;
  max-height: calc(100vh - 286px);
}

.schedule-board {
  display: grid;
  min-width: 1160px;
  border: 1px solid #d0e8eb;
  border-radius: 8px;
  background: #fff;
}

.schedule-board__corner,
.schedule-board__day,
.schedule-board__person,
.schedule-board__cell {
  min-width: 0;
  border-right: 1px solid #e8f4f6;
  border-bottom: 1px solid #e8f4f6;
}

.schedule-board__corner {
  position: sticky;
  top: 0;
  left: 0;
  z-index: 5;
  min-height: 82px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 12px;
  background:
    linear-gradient(27deg, transparent 49.2%, #d0e8eb 50%, transparent 50.8%),
    #f9fcfd;
  color: #64748b;
  font-size: 13px;
}

.schedule-board__corner strong {
  align-self: flex-start;
  color: #0e7b85;
  font-size: 13px;
}

.schedule-board__corner span {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.schedule-board__day {
  position: sticky;
  top: 0;
  z-index: 3;
  min-height: 82px;
  padding: 10px 10px 8px;
  background: #f9fcfd;
  box-sizing: border-box;
  text-align: center;
}

.schedule-board__day strong,
.schedule-board__day span,
.schedule-board__day em {
  display: block;
  text-align: center;
}

.schedule-board__day strong {
  display: inline-block;
  margin-right: 8px;
  color: #0e7b85;
  font-size: 14px;
}

.schedule-board__day span {
  display: inline-block;
  margin-top: 0;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.schedule-board__day em {
  margin-top: 5px;
  color: #94a3b8;
  font-size: 11px;
  font-style: normal;
}

.schedule-board__day--today {
  background: linear-gradient(180deg, #f0f8f9 0%, #e6f9fa 100%);
}

.schedule-board__day--weekend span,
.schedule-board__day--weekend em {
  color: #d97706;
}

.schedule-board__ticks {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin-top: 10px;
  color: #94a3b8;
  font-size: 11px;
}

.schedule-board__person {
  position: sticky;
  left: 0;
  z-index: 2;
  min-height: 164px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  background: #fff;
  box-sizing: border-box;
}

.doctor-avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  background: linear-gradient(135deg, #dbeafe, #ccfbf1);
  color: #0f766e;
  font-weight: 700;
}

.schedule-board__person strong {
  display: block;
  color: #0f172a;
  font-size: 14px;
}

.schedule-board__person em {
  display: block;
  margin-top: 5px;
  color: #64748b;
  font-size: 12px;
  font-style: normal;
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.schedule-board__cell {
  min-height: 164px;
  padding: 8px;
  display: grid;
  grid-template-rows: repeat(2, minmax(46px, 1fr));
  gap: 6px;
  background: #fff;
  box-sizing: border-box;
}

.schedule-board__cell--today {
  background: #f0fdf9;
}

.schedule-board__cell--weekend {
  background: #fffaf0;
}

.schedule-board__period-slot {
  min-height: 46px;
  position: relative;
  border: 1px dashed #e2e8f0;
  border-radius: 6px;
  padding: 4px;
  display: flex;
  flex-direction: column;
  background: rgb(248 250 252 / 58%);
}

.schedule-board__empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: #cbd5e1;
  font-size: 12px;
  pointer-events: none;
}

.schedule-shift {
  position: relative;
  z-index: 1;
  min-height: 70px;
  padding: 8px 8px 7px;
  border-radius: 6px;
  border-left: 4px solid #4aa564;
  background: #ddf7dc;
  box-shadow: 0 1px 2px rgb(15 23 42 / 8%);
  box-sizing: border-box;
  overflow: hidden;
}

.schedule-shift--afternoon {
  border-left-color: #2f91b4;
  background: #d9f1fb;
}

.schedule-shift--suspended {
  border-left-color: #94a3b8;
  background: #e5e7eb;
}

.schedule-shift--full-status {
  border-left-color: #f59e0b;
  background: #fff3c4;
}

.schedule-shift__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.schedule-shift__head strong {
  min-width: 0;
  color: #0f172a;
  font-size: 12px;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.schedule-shift__head span {
  height: 18px;
  padding: 0 5px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  background: #f8fafc;
  color: #0f766e;
  font-size: 11px;
  font-weight: 700;
}

.schedule-shift p,
.schedule-shift em,
.schedule-shift small {
  display: block;
  margin: 5px 0 0;
  color: #64748b;
  font-size: 11px;
  line-height: 1.35;
  font-style: normal;
}

@media (max-width: 900px) {
  .doctor-schedule-page {
    padding: 12px;
  }

  .personal-center-layout {
    grid-template-columns: 1fr;
  }

  .personal-sidebar {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 12px;
    align-items: center;
  }

  .personal-card {
    padding: 0;
    border-bottom: 0;
    text-align: left;
  }

  .personal-card .personal-avatar {
    display: none;
  }

  .personal-nav {
    display: flex;
    padding-top: 0;
  }

  .schedule-header,
  .schedule-board-tools {
    align-items: flex-start;
    flex-direction: column;
  }

  .schedule-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .profile-grid {
    grid-template-columns: 1fr;
  }
}
</style>

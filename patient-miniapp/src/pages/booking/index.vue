<template>
  <patient-nav-bar title="线上挂号" />
  <view class="page booking-page">
    <view v-if="aiConsultation" class="ai-card">
      <view class="ai-title">AI 推荐</view>
      <view class="ai-desc">{{ aiConsultation.summary }}</view>
      <view class="ai-meta">推荐科室：{{ aiConsultation.recommendedDepartmentName }} · 风险等级：{{ aiConsultation.riskLevel }}</view>
    </view>

    <view v-if="!isSearchDoctorEntry" class="section-card">
      <view class="section-title">选择科室</view>
      <view v-if="isSearchDepartmentEntry" class="single-chip-row">
        <view class="dept-chip active">{{ selectedDepartment?.name }}</view>
      </view>
      <scroll-view v-else scroll-x class="chip-scroll">
        <view class="chip-row">
          <view
            v-for="department in departments"
            :key="department.id"
            :class="['dept-chip', selectedDepartmentId === department.id ? 'active' : '']"
            @tap="selectDepartment(department.id)"
          >
            {{ department.name }}
          </view>
        </view>
      </scroll-view>
    </view>

    <view v-if="availableDates.length" class="date-strip">
      <scroll-view scroll-x>
        <view class="date-row">
          <view
            v-for="date in availableDates"
            :key="date"
            :class="['date-card', selectedDate === date ? 'active' : '']"
            @tap="selectDate(date)"
          >
            <view>{{ weekdayLabel(date) }}</view>
            <view>{{ shortDate(date) }}</view>
            <view class="date-status">有号</view>
          </view>
        </view>
      </scroll-view>
    </view>

    <view v-if="selectedSchedule" class="doctor-detail">
      <view class="doctor-banner">
        <view class="avatar">{{ selectedSchedule.doctorName.slice(0, 1) }}</view>
        <view>
          <view class="detail-name">{{ selectedSchedule.doctorName }}</view>
          <view class="detail-meta">{{ selectedDepartment?.name }} · {{ doctorInfo(selectedSchedule.doctorId) }}</view>
        </view>
      </view>

      <view class="level-row">
        <view><text class="accent-bar"></text>级别：{{ doctorInfo(selectedSchedule.doctorId) }}</view>
      </view>

      <view class="time-list">
        <view
          v-for="slot in selectedSchedule.timeSlots"
          :key="slot.id"
          :class="['time-row', slot.available <= 0 ? 'disabled' : '']"
          @tap="selectSlot(selectedSchedule, slot)"
        >
          <view class="time-value">{{ slot.startTime }}</view>
          <view class="slot-price">¥{{ registrationFeeText(selectedSchedule.doctorId) }}</view>
          <view class="slot-button">
            <text>{{ slot.available > 0 ? '可约' : '满号' }}</text>
            <text class="slot-left">余号{{ slot.available }}</text>
          </view>
        </view>
      </view>
    </view>

    <view v-for="schedule in filteredSchedules" v-else :key="schedule.id" class="doctor-card" @tap="openSchedule(schedule)">
      <view class="doctor-main">
        <view class="avatar">{{ schedule.doctorName.slice(0, 1) }}</view>
        <view class="doctor-info">
          <view class="doctor-line">
            <text class="doctor">{{ schedule.doctorName }}</text>
            <text class="doctor-title">{{ doctorInfo(schedule.doctorId) }}</text>
          </view>
          <view class="dept-line">{{ selectedDepartment?.name || '门诊科室' }}</view>
          <view class="price-line">¥{{ registrationFeeText(schedule.doctorId) }}</view>
          <view class="muted">擅长：{{ doctorInfo(schedule.doctorId) }}</view>
        </view>
        <view :class="['status-badge', schedule.available > 0 ? 'available' : 'full']">
          <text>{{ schedule.period }}</text>
          <text>{{ schedule.available > 0 ? '有号' : '满号' }}</text>
        </view>
      </view>
      <view v-if="isRecommendedDoctor(schedule.doctorId)" class="doctor-actions">
        <view class="recommend-tag">AI 推荐</view>
      </view>
    </view>

    <view v-if="pendingBooking" class="confirm-mask">
      <view class="confirm-dialog">
        <view class="dialog-close" @tap="pendingBooking = null">×</view>
        <view class="dialog-title">请核对以下挂号信息</view>
        <view class="dialog-body">
          <view class="confirm-row">
            <text class="confirm-label">就诊人：</text>
            <text class="confirm-value">{{ auth.boundPatient?.name || '当前就诊人' }}</text>
          </view>
          <view class="confirm-row">
            <text class="confirm-label">预约科室：</text>
            <text>{{ selectedDepartment?.name || '—' }}</text>
          </view>
          <view class="confirm-row">
            <text class="confirm-label">预约医生：</text>
            <text>{{ pendingBooking.schedule.doctorName }}</text>
          </view>
          <view class="confirm-row">
            <text class="confirm-label">预约时间：</text>
            <text>{{ formatDate(pendingBooking.schedule.workDate) }} {{ pendingBooking.slot.period || pendingBooking.schedule.period }} {{ pendingBooking.slot.startTime }}</text>
          </view>
          <view class="confirm-row">
            <text class="confirm-label">挂号费：</text>
            <text class="slot-price">¥{{ registrationFeeText(pendingBooking.schedule.doctorId) }}</text>
          </view>
        </view>
        <view class="dialog-actions">
          <button class="dialog-secondary" @tap="pendingBooking = null">返回修改</button>
          <button class="dialog-primary" @tap="confirmBooking()">确认挂号并缴费</button>
        </view>
      </view>
    </view>

    <view v-if="selectedDepartmentId && !filteredSchedules.length && !selectedSchedule" class="empty-card">
      当前日期暂无可预约医生排班
    </view>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app';
import { computed, onMounted, ref } from 'vue';
import { request } from '../../api/http';
import { useAuthStore } from '../../stores/auth';
import { formatDate } from '../../utils/format';

interface Department {
  id: string;
  name: string;
}

interface Doctor {
  id: string;
  employeeNo?: string;
  name: string;
  title: string;
  departmentId: string;
  specialty: string;
}

interface Schedule {
  id: string;
  doctorId: string;
  doctorName: string;
  departmentId: string;
  workDate: string;
  period: string;
  capacity: number;
  booked: number;
  locked: number;
  available: number;
  status: string;
  timeSlots: TimeSlot[];
}

interface TimeSlot {
  id: string;
  period?: string;
  startTime: string;
  capacity: number;
  booked: number;
  locked: number;
  available: number;
}

interface Appointment {
  id: string;
}

interface AiConsultation {
  summary: string;
  riskLevel: string;
  recommendedDepartmentId: string;
  recommendedDepartmentName: string;
  recommendedDoctors: Array<{ doctorId: string; doctorName: string; reason: string }>;
  recordDraft: string;
}

const auth = useAuthStore();
const departments = ref<Department[]>([]);
const doctors = ref<Doctor[]>([]);
const selectedDepartmentId = ref('');
const schedules = ref<Schedule[]>([]);
const selectedDate = ref('');
const selectedSchedule = ref<Schedule | null>(null);
const pendingBooking = ref<{ schedule: Schedule; slot: TimeSlot } | null>(null);
const aiConsultation = ref<AiConsultation>();
const initialDepartmentId = ref('');
const initialDoctorId = ref('');
const focusedDoctorId = ref('');
const isSearchDoctorEntry = ref(false);
const EXCLUDED_PATIENT_DEPARTMENT_NAMES = ['检查科', '检验科', '处置科', '药房', '收费处', '系统管理'];

const selectedDepartment = computed(() => departments.value.find((item) => item.id === selectedDepartmentId.value));
const recommendedDoctorIds = computed(() => new Set((aiConsultation.value?.recommendedDoctors ?? []).map((item) => item.doctorId)));
const BOOKABLE_DAY_SPAN = 7;

function formatDateKey(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function addDays(date: Date, days: number) {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
}

function isWithinBookingWindow(workDate: string) {
  if (!workDate) {
    return false;
  }
  const today = new Date();
  const startDate = formatDateKey(today);
  const endDate = formatDateKey(addDays(today, BOOKABLE_DAY_SPAN - 1));
  return workDate >= startDate && workDate <= endDate;
}

const bookableSchedules = computed(() =>
  schedules.value.filter((item) => isWithinBookingWindow(item.workDate))
);
const visibleSchedules = computed(() =>
  focusedDoctorId.value
    ? bookableSchedules.value.filter((item) => item.doctorId === focusedDoctorId.value)
    : bookableSchedules.value
);
const availableDates = computed(() => Array.from(new Set(visibleSchedules.value.map((item) => item.workDate))).sort());
const doctorMap = computed(() => new Map(doctors.value.map((item) => [item.id, item])));
const displaySchedules = computed(() => aggregateSchedules(visibleSchedules.value));
const filteredSchedules = computed(() =>
  displaySchedules.value
    .filter((item) => !selectedDate.value || item.workDate === selectedDate.value)
    .sort((a, b) => {
      const ar = isRecommendedDoctor(a.doctorId) ? 0 : 1;
      const br = isRecommendedDoctor(b.doctorId) ? 0 : 1;
      if (ar !== br) {
        return ar - br;
      }
      return a.period.localeCompare(b.period, 'zh-CN');
    })
);
const selectedDepartmentIndex = computed(() => optionIndexById(departments.value, selectedDepartmentId.value));
const selectedDateIndex = computed(() => optionIndexByValue(availableDates.value, selectedDate.value));
const isSearchDepartmentEntry = computed(() => !!initialDepartmentId.value && !focusedDoctorId.value);

function doctorInfo(doctorId: string) {
  const doctor = doctorMap.value.get(doctorId);
  if (!doctor) {
    return '医生信息加载中';
  }
  const extra = [doctor.title, doctor.specialty].filter(Boolean).join(' · ');
  return extra || '门诊医生';
}

function isSeniorDoctorTitle(title: string) {
  return /主任|高级|专家/.test(title);
}

function registrationFee(doctorId: string) {
  const doctor = doctorMap.value.get(doctorId);
  return isSeniorDoctorTitle(doctor?.title || '') ? 40 : 15;
}

function registrationFeeText(doctorId: string) {
  return registrationFee(doctorId).toFixed(2);
}

function isRecommendedDoctor(doctorId: string) {
  const doctor = doctorMap.value.get(doctorId);
  return recommendedDoctorIds.value.has(doctorId) || (!!doctor?.employeeNo && recommendedDoctorIds.value.has(doctor.employeeNo));
}

function normalizeText(value: unknown) {
  if (typeof value === 'string') {
    return value;
  }
  if (typeof value === 'number') {
    return String(value);
  }
  if (value && typeof value === 'object') {
    const record = value as Record<string, unknown>;
    const year = record.year;
    const month = record.monthValue ?? record.month;
    const day = record.dayOfMonth ?? record.day;
    if (typeof year === 'number' && typeof month === 'number' && typeof day === 'number') {
      return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    }
    if (typeof record.name === 'string') {
      return record.name;
    }
  }
  return '';
}

function toDepartment(item: Record<string, unknown>): Department {
  return {
    id: normalizeText(item.id),
    name: normalizeText(item.name)
  };
}

function normalizeSearchText(value: string) {
  return value.normalize('NFKC').toLocaleLowerCase().replace(/\s+/g, '');
}

function isPatientSelectableDepartment(department: Department) {
  const departmentName = normalizeSearchText(department.name);
  return !EXCLUDED_PATIENT_DEPARTMENT_NAMES.some((name) => departmentName.includes(normalizeSearchText(name)));
}

function toDoctor(item: Record<string, unknown>): Doctor {
  return {
    id: normalizeText(item.id),
    employeeNo: normalizeText(item.employeeNo),
    name: normalizeText(item.name),
    title: normalizeText(item.title),
    departmentId: normalizeText(item.departmentId),
    specialty: normalizeText(item.specialty)
  };
}

function toSchedule(item: Record<string, unknown>): Schedule {
  return {
    id: normalizeText(item.id),
    doctorId: normalizeText(item.doctorId),
    doctorName: normalizeText(item.doctorName),
    departmentId: normalizeText(item.departmentId),
    workDate: normalizeText(item.workDate),
    period: normalizeText(item.period),
    capacity: Number(item.capacity ?? 0),
    booked: Number(item.booked ?? 0),
    locked: Number(item.locked ?? 0),
    available: Number(item.available ?? 0),
    status: normalizeText(item.status),
    timeSlots: Array.isArray(item.timeSlots)
      ? item.timeSlots.map((slot) => toTimeSlot(slot as Record<string, unknown>))
      : []
  };
}

function toTimeSlot(item: Record<string, unknown>): TimeSlot {
  return {
    id: normalizeText(item.id),
    period: normalizeText(item.period),
    startTime: normalizeText(item.startTime).slice(0, 5),
    capacity: Number(item.capacity ?? 0),
    booked: Number(item.booked ?? 0),
    locked: Number(item.locked ?? 0),
    available: Number(item.available ?? 0)
  };
}

function withSlotPeriod(schedule: Schedule): Schedule {
  return {
    ...schedule,
    timeSlots: schedule.timeSlots.map((slot) => ({ ...slot, period: slot.period || schedule.period }))
  };
}

function aggregateSchedules(items: Schedule[]) {
  const groups = new Map<string, Schedule[]>();
  items.forEach((item) => {
    const key = `${item.doctorId}|${item.departmentId}|${item.workDate}`;
    groups.set(key, [...(groups.get(key) || []), item]);
  });

  return Array.from(groups.values()).flatMap((group) => {
    const morning = group.find((item) => item.period === '上午');
    const afternoon = group.find((item) => item.period === '下午');
    const others = group.filter((item) => item.period !== '上午' && item.period !== '下午').map(withSlotPeriod);
    if (!morning || !afternoon) {
      return [...group.filter((item) => item.period === '上午' || item.period === '下午').map(withSlotPeriod), ...others];
    }
    const timeSlots = [...morning.timeSlots, ...afternoon.timeSlots]
      .map((slot) => ({
        ...slot,
        period: slot.period || (slot.startTime >= '12:00' ? '下午' : '上午')
      }))
      .sort((a, b) => a.startTime.localeCompare(b.startTime));
    const combined: Schedule = {
      ...morning,
      id: `${morning.id}__${afternoon.id}`,
      period: '全天',
      capacity: morning.capacity + afternoon.capacity,
      booked: morning.booked + afternoon.booked,
      locked: morning.locked + afternoon.locked,
      available: morning.available + afternoon.available,
      timeSlots
    };
    return [combined, ...others];
  });
}

function optionIndexById(options: Array<{ id: string }>, value: string) {
  const index = options.findIndex((item) => item.id === value);
  return index >= 0 ? index : 0;
}

function optionIndexByValue(options: string[], value: string) {
  const index = options.findIndex((item) => item === value);
  return index >= 0 ? index : 0;
}

function resolveInitialDepartmentId() {
  if (initialDepartmentId.value && departments.value.some((item) => item.id === initialDepartmentId.value)) {
    return initialDepartmentId.value;
  }

  const recommendedDepartmentId = aiConsultation.value?.recommendedDepartmentId;
  if (recommendedDepartmentId && departments.value.some((item) => item.id === recommendedDepartmentId)) {
    return recommendedDepartmentId;
  }
  const recommendedDepartmentName = aiConsultation.value?.recommendedDepartmentName;
  if (recommendedDepartmentName) {
    const matched = departments.value.find((item) => item.name === recommendedDepartmentName);
    if (matched) {
      return matched.id;
    }
  }
  return departments.value[0]?.id || '';
}

async function selectDepartment(departmentId: string) {
  if (selectedDepartmentId.value === departmentId) {
    return;
  }
  selectedSchedule.value = null;
  pendingBooking.value = null;
  focusedDoctorId.value = '';
  initialDoctorId.value = '';
  selectedDepartmentId.value = departmentId;
  await loadDepartmentResources();
}

function selectDate(date: string) {
  selectedSchedule.value = null;
  pendingBooking.value = null;
  selectedDate.value = date;
  openFocusedDoctorScheduleForDate();
}

function shortDate(date: string) {
  return date.slice(5);
}

function weekdayLabel(date: string) {
  const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
  const parsed = new Date(`${date}T00:00:00`);
  if (Number.isNaN(parsed.getTime())) {
    return '日期';
  }
  return weekdays[parsed.getDay()];
}

function syncSelectedDate() {
  if (!availableDates.value.length) {
    selectedDate.value = '';
    return;
  }
  if (!availableDates.value.includes(selectedDate.value)) {
    selectedDate.value = availableDates.value[0];
  }
}

function openFocusedDoctorScheduleForDate() {
  if (!focusedDoctorId.value || !selectedDate.value) {
    return;
  }

  selectedSchedule.value = displaySchedules.value.find((item) => item.workDate === selectedDate.value) ?? null;
}

function openInitialDoctorSchedule() {
  if (!initialDoctorId.value) {
    return;
  }

  focusedDoctorId.value = initialDoctorId.value;
  syncSelectedDate();

  const matched = displaySchedules.value.find((item) => item.workDate === selectedDate.value) ?? displaySchedules.value[0];
  if (!matched) {
    uni.showToast({ title: '该医生暂无可约排班', icon: 'none' });
    return;
  }

  selectedDate.value = matched.workDate;
  selectedSchedule.value = matched;
  initialDoctorId.value = '';
}

async function loadDepartmentResources() {
  if (!selectedDepartmentId.value) {
    schedules.value = [];
    doctors.value = [];
    selectedDate.value = '';
    return;
  }

  const [doctorList, scheduleList] = await Promise.all([
    request<Record<string, unknown>[]>({ url: `/doctors?departmentId=${selectedDepartmentId.value}`, method: 'GET' }),
    request<Record<string, unknown>[]>({ url: `/schedules?departmentId=${selectedDepartmentId.value}`, method: 'GET' })
  ]);

  doctors.value = doctorList.map(toDoctor);
  schedules.value = scheduleList.map(toSchedule);
  if (initialDoctorId.value) {
    openInitialDoctorSchedule();
  } else {
    syncSelectedDate();
  }
}

async function onDepartmentChange(event: { detail: { value: string } }) {
  selectedSchedule.value = null;
  pendingBooking.value = null;
  selectedDepartmentId.value = departments.value[Number(event.detail.value)]?.id ?? '';
  await loadDepartmentResources();
}

function onDateChange(event: { detail: { value: string } }) {
  selectedSchedule.value = null;
  pendingBooking.value = null;
  selectedDate.value = availableDates.value[Number(event.detail.value)] ?? '';
}

function selectSlot(schedule: Schedule, slot: TimeSlot) {
  if (slot.available <= 0) {
    return;
  }
  pendingBooking.value = { schedule, slot };
}

function openSchedule(schedule: Schedule) {
  if (!schedule.timeSlots.length) {
    uni.showToast({ title: '暂无可约时间', icon: 'none' });
    return;
  }
  selectedSchedule.value = schedule;
}

async function confirmBooking() {
  if (!pendingBooking.value) {
    return;
  }

  const { schedule, slot } = pendingBooking.value;
  let patient;
  try {
    patient = auth.requireBoundPatient();
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' });
    uni.navigateTo({ url: '/pages/real-name/index' });
    return;
  }

  try {
    const appointmentPeriod = slot.period || schedule.period;
    const appointment = await request<Appointment>({
      url: '/appointments',
      method: 'POST',
      data: {
        scheduleId: slot.id,
        patientId: patient.id,
        patientName: patient.name,
        doctorId: schedule.doctorId,
        doctorName: schedule.doctorName,
        departmentId: schedule.departmentId,
        departmentName: selectedDepartment.value?.name,
        visitDate: schedule.workDate,
        period: appointmentPeriod,
        startTime: slot.startTime,
        source: 'AI',
        triageSummary: aiConsultation.value?.recordDraft || aiConsultation.value?.summary || '',
        riskLevel: aiConsultation.value?.riskLevel || 'LOW',
        recommendedDepartmentId: aiConsultation.value?.recommendedDepartmentId || selectedDepartmentId.value,
        registrationFee: registrationFee(schedule.doctorId)
      }
    });

    await request({
      url: '/payments/orders',
      method: 'POST',
      data: {
        businessType: 'APPOINTMENT',
        businessId: appointment.id,
        patientId: patient.id,
        amount: registrationFee(schedule.doctorId),
        paymentMethod: 'WECHAT_TEST'
      }
    });

    uni.showToast({ title: '已生成待缴费挂号单', icon: 'none' });
    pendingBooking.value = null;
    await loadDepartmentResources();
    setTimeout(() => {
      uni.navigateTo({ url: '/pages/pending-payments/index' });
    }, 400);
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' });
  }
}

async function initialize() {
  aiConsultation.value = uni.getStorageSync('last_ai_consultation') || undefined;
  const departmentList = await request<Record<string, unknown>[]>({ url: '/departments', method: 'GET' });
  departments.value = departmentList
    .map(toDepartment)
    .filter((item) => item.id && item.name && isPatientSelectableDepartment(item));
  selectedDepartmentId.value = resolveInitialDepartmentId();
  await loadDepartmentResources();
}

onLoad((options) => {
  initialDepartmentId.value = decodeURIComponent(String(options?.departmentId || ''));
  initialDoctorId.value = decodeURIComponent(String(options?.doctorId || ''));
  isSearchDoctorEntry.value = String(options?.from || '') === 'doctorSearch' && !!initialDoctorId.value;
});

onMounted(initialize);
</script>

<style scoped>
.booking-page {
  padding-top: 0;
  background: #f2f7ff;
}

.ai-card,
.section-card,
.doctor-card,
.doctor-detail,
.empty-card {
  margin-bottom: 22rpx;
  padding: 28rpx;
  border-radius: 18rpx;
  background: #fff;
  box-shadow: 0 10rpx 30rpx rgba(31, 84, 140, 0.08);
}

.ai-card {
  border-left: 8rpx solid #2f80ed;
}

.ai-title,
.section-title {
  color: #172033;
  font-size: 34rpx;
  font-weight: 800;
}

.ai-desc {
  margin-top: 12rpx;
  color: #334155;
  font-size: 28rpx;
  line-height: 1.55;
}

.ai-meta {
  margin-top: 12rpx;
  color: #2f80ed;
  font-size: 26rpx;
}

.chip-scroll {
  margin-top: 20rpx;
  white-space: nowrap;
}

.single-chip-row {
  display: flex;
  justify-content: center;
  margin-top: 20rpx;
}

.chip-row,
.date-row {
  display: flex;
  gap: 16rpx;
}

.dept-chip {
  flex-shrink: 0;
  padding: 16rpx 28rpx;
  border-radius: 999rpx;
  background: #f1f6fd;
  color: #334155;
  font-size: 28rpx;
  font-weight: 700;
}

.dept-chip.active {
  background: #2f80ed;
  color: #fff;
}

.date-strip {
  margin: 0 -24rpx 22rpx;
  padding: 20rpx 24rpx;
  background: #fff;
  box-shadow: 0 8rpx 20rpx rgba(31, 84, 140, 0.06);
}

.date-card {
  flex-shrink: 0;
  width: 112rpx;
  padding: 18rpx 8rpx 12rpx;
  border-radius: 10rpx;
  background: #f6f8fb;
  color: #2f3542;
  font-size: 28rpx;
  line-height: 1.35;
  text-align: center;
}

.date-card.active {
  background: #2f80ed;
  color: #fff;
}

.date-status {
  margin-top: 8rpx;
  color: #2f80ed;
  font-size: 26rpx;
  font-weight: 700;
}

.date-card.active .date-status {
  color: #fff;
}

.doctor-main {
  display: flex;
  gap: 20rpx;
  align-items: flex-start;
}

.avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 104rpx;
  height: 104rpx;
  border-radius: 12rpx;
  background: linear-gradient(135deg, #dbeafe 0%, #93c5fd 100%);
  color: #1d4ed8;
  font-size: 42rpx;
  font-weight: 900;
}

.doctor-info {
  flex: 1;
  min-width: 0;
}

.doctor-line {
  display: flex;
  align-items: baseline;
  gap: 14rpx;
  flex-wrap: wrap;
}

.doctor {
  color: #111827;
  font-size: 36rpx;
  font-weight: 900;
}

.doctor-title {
  color: #111827;
  font-size: 27rpx;
  font-weight: 700;
}

.dept-line {
  margin-top: 10rpx;
  color: #334155;
  font-size: 28rpx;
}

.price-line,
.slot-price {
  color: #e6821e;
  font-size: 32rpx;
  font-weight: 800;
}

.price-line {
  margin: 10rpx 0;
}

.status-badge {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  min-width: 112rpx;
  overflow: hidden;
  border: 2rpx solid;
  border-radius: 10rpx;
  font-size: 27rpx;
  font-weight: 800;
  text-align: center;
}

.status-badge text {
  padding: 8rpx 14rpx;
}

.status-badge.available {
  border-color: #30c47c;
  color: #30a873;
}

.status-badge.available text:first-child {
  background: #30c47c;
  color: #fff;
}

.status-badge.full {
  border-color: #ff8a00;
  color: #ff8a00;
}

.status-badge.full text:first-child {
  background: #ff8a00;
  color: #fff;
}

.doctor-actions {
  display: flex;
  align-items: center;
  min-height: 44rpx;
  margin-top: 22rpx;
  padding-top: 20rpx;
  border-top: 1px solid #edf2f7;
}

.recommend-tag {
  padding: 7rpx 16rpx;
  border-radius: 999rpx;
  background: #ecfdf5;
  color: #059669;
  font-size: 24rpx;
  font-weight: 700;
}

.doctor-detail {
  padding: 0;
  overflow: hidden;
}

.doctor-banner {
  display: flex;
  align-items: center;
  gap: 22rpx;
  padding: 34rpx 30rpx;
  background: #3d98f4;
  color: #fff;
}

.detail-name {
  font-size: 36rpx;
  font-weight: 900;
}

.detail-meta {
  margin-top: 12rpx;
  color: rgba(255, 255, 255, 0.86);
  font-size: 27rpx;
}

.level-row {
  display: flex;
  align-items: center;
  padding: 28rpx 30rpx;
  border-bottom: 1px solid #edf2f7;
  color: #1f2937;
  font-size: 31rpx;
  font-weight: 800;
}

.accent-bar {
  display: inline-block;
  width: 8rpx;
  height: 34rpx;
  margin-right: 14rpx;
  border-radius: 999rpx;
  background: #2f80ed;
  vertical-align: -6rpx;
}

.time-list {
  display: flex;
  flex-direction: column;
}

.time-row {
  display: grid;
  grid-template-columns: 1fr 160rpx 144rpx;
  align-items: center;
  gap: 18rpx;
  min-height: 116rpx;
  padding: 0 30rpx;
  border-bottom: 1px solid #edf2f7;
}

.time-row.disabled {
  opacity: 0.72;
}

.time-value {
  color: #111827;
  font-size: 34rpx;
  font-weight: 700;
}

.slot-button {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 82rpx;
  margin: 0;
  padding: 0;
  border: 2rpx solid #30c47c;
  border-radius: 10rpx;
  overflow: hidden;
  background: #fff;
  color: #30a873;
  font-size: 26rpx;
  font-weight: 800;
  line-height: 1;
}

.slot-button text {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  width: 100%;
}

.slot-button text:first-child {
  background: #30c47c;
  color: #fff;
}

.time-row.disabled .slot-button {
  border-color: #cbd5e1;
  color: #94a3b8;
}

.time-row.disabled .slot-button text:first-child {
  background: #cbd5e1;
}

.slot-left {
  background: #fff;
  color: #30a873;
}

.time-row.disabled .slot-left {
  color: #94a3b8;
}

.confirm-mask {
  position: fixed;
  inset: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48rpx;
  background: rgba(0, 0, 0, 0.62);
}

.confirm-dialog {
  position: relative;
  width: 100%;
  overflow: hidden;
  border-radius: 30rpx;
  background: #fff;
}

.dialog-close {
  position: absolute;
  top: 26rpx;
  right: 30rpx;
  color: #111827;
  font-size: 54rpx;
  line-height: 1;
}

.dialog-title {
  padding: 64rpx 36rpx 28rpx;
  color: #1f2937;
  font-size: 42rpx;
  font-weight: 800;
  text-align: center;
}

.dialog-body {
  padding: 10rpx 46rpx 40rpx;
}

.confirm-row {
  display: flex;
  gap: 18rpx;
  padding: 18rpx 0;
  color: #1f2937;
  font-size: 31rpx;
  line-height: 1.45;
}

.confirm-label {
  flex-shrink: 0;
  color: #7b8494;
}

.confirm-value {
  color: #2f80ed;
}

.dialog-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  border-top: 1px solid #edf2f7;
}

.dialog-secondary,
.dialog-primary {
  height: 96rpx;
  margin: 0;
  border-radius: 0;
  background: #fff;
  font-size: 32rpx;
  line-height: 96rpx;
}

.dialog-secondary {
  color: #1f2937;
  border-right: 1px solid #edf2f7;
}

.dialog-primary {
  color: #2f80ed;
}

.empty-card {
  color: #64748b;
  font-size: 29rpx;
  text-align: center;
}
</style>

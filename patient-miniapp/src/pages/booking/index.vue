<template>
  <patient-nav-bar title="线上挂号" />
  <view class="page booking-page">
    <view v-if="aiConsultation" class="ai-card">
      <view class="ai-title">AI 推荐</view>
      <view class="ai-desc">{{ aiConsultation.summary }}</view>
      <view class="ai-meta">推荐科室：{{ aiConsultation.recommendedDepartmentName }} · 风险等级：{{ riskText }}</view>
    </view>

    <view class="search-card">
      <view class="search-row">
        <view class="search-icon"></view>
        <input
          v-model="searchKeyword"
          class="search-input"
          placeholder="搜索医生姓名"
          placeholder-class="search-placeholder"
          @input="onSearchInput"
        />
        <text v-if="searchKeyword" class="search-clear" @tap="clearSearch">×</text>
      </view>
      <view v-if="searchKeyword && searchResults.length" class="search-results">
        <view
          v-for="doctor in searchResults"
          :key="doctor.id"
          class="search-result-item"
          @tap="selectSearchedDoctor(doctor)"
        >
          <view class="result-main">
            <text class="result-name">{{ doctor.name }}</text>
            <text class="result-title">{{ doctor.title }}</text>
          </view>
          <text class="result-specialty" v-if="doctor.specialty">{{ doctor.specialty }}</text>
          <text class="result-dept">{{ doctor.departmentName || '' }}</text>
        </view>
      </view>
      <view v-else-if="searchKeyword && !searchResults.length" class="search-empty">未找到匹配的医生</view>
    </view>

    <view v-if="!isSearchDoctorEntry" class="section-card">
      <view class="section-title">选择科室</view>
      <view v-if="isSearchDepartmentEntry" class="single-chip-row">
        <view class="dept-chip active">{{ selectedDepartment?.name }}</view>
      </view>
      <view v-else class="dept-grid">
        <view
          v-for="department in departments"
          :key="department.id"
          :class="['dept-chip', selectedDepartmentId === department.id ? 'active' : '']"
          @tap="selectDepartment(department.id)"
        >
          {{ department.name }}
        </view>
      </view>
    </view>

    <view v-if="loadingSchedules" class="empty-card">
      <view class="empty-title">加载号源中…</view>
      <view class="empty-desc">正在查询可挂号源，请稍候</view>
    </view>
    <view v-else-if="availableDates.length" class="date-strip">
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
    <view v-else-if="selectedDepartmentId && !loadingDepartments && !loadingSchedules" class="empty-card">
      <view class="empty-title">暂无可挂号源</view>
      <view class="empty-desc">该科室近期暂无排班，请选择其他科室或稍后再试</view>
    </view>

    <view v-if="selectedSchedule" class="doctor-detail">
      <view class="doctor-banner">
        <view class="avatar">{{ selectedSchedule.doctorName.slice(0, 1) }}</view>
        <view>
          <view class="detail-name">{{ selectedSchedule.doctorName }}</view>
          <view class="detail-meta">{{ selectedDepartment?.name }} · {{ doctorTitle(selectedSchedule.doctorId) }}</view>
          <view class="detail-specialty" v-if="doctorSpecialty(selectedSchedule.doctorId)">{{ doctorSpecialty(selectedSchedule.doctorId) }}</view>
        </view>
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
            <text class="doctor-title">{{ doctorTitle(schedule.doctorId) }}</text>
          </view>
          <view class="specialty-line" v-if="doctorSpecialty(schedule.doctorId)">{{ doctorSpecialty(schedule.doctorId) }}</view>
          <view class="dept-line">{{ selectedDepartment?.name || '门诊科室' }} · ¥{{ registrationFeeText(schedule.doctorId) }}</view>
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
import { onLoad, onShow } from '@dcloudio/uni-app';
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
  departmentName?: string;
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
  patientId?: string;
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
const searchableDoctors = ref<Doctor[]>([]);
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
const loadingDepartments = ref(false);
const loadingSchedules = ref(false);
const searchKeyword = ref('');
const EXCLUDED_PATIENT_DEPARTMENT_NAMES = ['检查科', '检验科', '处置科', '药房', '收费处', '系统管理'];

const selectedDepartment = computed(() => departments.value.find((item) => item.id === selectedDepartmentId.value));
const recommendedDoctorIds = computed(() => new Set((aiConsultation.value?.recommendedDoctors ?? []).map((item) => item.doctorId)));
const riskText = computed(() => {
  const level = (aiConsultation.value?.riskLevel || '').toUpperCase();
  if (level === 'HIGH' || level === '高') return '高风险';
  if (level === 'MEDIUM' || level === '中') return '中风险';
  return '低风险';
});
const BOOKABLE_DAY_SPAN = 14;

const searchResults = computed(() => {
  const kw = searchKeyword.value.trim();
  if (!kw) return [];
  return searchableDoctors.value
    .filter((d) => d.name.includes(kw))
    .slice(0, 6)
    .map((d) => ({
      ...d,
      departmentName: d.departmentName || departments.value.find((dept) => dept.id === d.departmentId)?.name || ''
    }));
});

function onSearchInput() {
  // reactive via searchKeyword binding
}

function clearSearch() {
  searchKeyword.value = '';
}

async function selectSearchedDoctor(doctor: Doctor & { departmentName: string }) {
  searchKeyword.value = '';
  selectedSchedule.value = null;
  pendingBooking.value = null;
  focusedDoctorId.value = doctor.id;
  if (doctor.departmentId && doctor.departmentId !== selectedDepartmentId.value) {
    selectedDepartmentId.value = doctor.departmentId;
    await loadDepartmentResources();
    return;
  }

  syncSelectedDate();
  openFocusedDoctorScheduleForDate();
}

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
const displaySchedules = computed(() =>
  focusedDoctorId.value ? visibleSchedules.value.map(withSlotPeriod) : aggregateSchedules(visibleSchedules.value)
);
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

function doctorTitle(doctorId: string) {
  const doctor = doctorMap.value.get(doctorId);
  return doctor?.title || '门诊医生';
}

function doctorSpecialty(doctorId: string) {
  const doctor = doctorMap.value.get(doctorId);
  return doctor?.specialty || '';
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
    departmentName: normalizeText(item.departmentName),
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

function aiConsultationStorageKey(patientId: string) {
  return `last_ai_consultation_${patientId}`;
}

function isAiConsultationForPatient(value: unknown, patientId: string): value is AiConsultation {
  if (!value || typeof value !== 'object') {
    return false;
  }
  return (value as AiConsultation).patientId === patientId;
}

function getStoredAiConsultation(patientId: string) {
  const scoped = uni.getStorageSync(aiConsultationStorageKey(patientId));
  if (isAiConsultationForPatient(scoped, patientId)) {
    return scoped;
  }

  const latest = uni.getStorageSync('last_ai_consultation');
  if (isAiConsultationForPatient(latest, patientId)) {
    return latest;
  }

  return undefined;
}

async function ensureCurrentPatientLoaded() {
  if (auth.boundPatient) {
    return auth.boundPatient;
  }
  if (!auth.token) {
    auth.restore();
  }
  if (!auth.boundPatient && auth.token) {
    try {
      await auth.loadProfile();
    } catch {
      // Keep booking usable even if profile refresh fails; AI recommendation will stay hidden.
    }
  }
  return auth.boundPatient;
}

async function loadCurrentPatientAiConsultation() {
  const patient = await ensureCurrentPatientLoaded();
  const patientId = patient?.id;
  aiConsultation.value = patientId ? getStoredAiConsultation(patientId) : undefined;
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
    selectedSchedule.value = null;
    return;
  }

  loadingSchedules.value = true;
  const [doctorList, scheduleList] = await Promise.all([
    request<Record<string, unknown>[]>({ url: `/doctors?departmentId=${selectedDepartmentId.value}`, method: 'GET' }),
    request<Record<string, unknown>[]>({ url: `/schedules?departmentId=${selectedDepartmentId.value}`, method: 'GET' })
  ]);

  doctors.value = doctorList.map(toDoctor);
  schedules.value = scheduleList.map(toSchedule);
  loadingSchedules.value = false;
  if (initialDoctorId.value) {
    openInitialDoctorSchedule();
  } else {
    syncSelectedDate();
    if (focusedDoctorId.value) {
      openFocusedDoctorScheduleForDate();
    }
  }
}

async function loadSearchableDoctors() {
  const doctorList = await request<Record<string, unknown>[]>({ url: '/doctors', method: 'GET' });
  searchableDoctors.value = doctorList.map(toDoctor);
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
  loadingDepartments.value = true;
  await loadCurrentPatientAiConsultation();
  const [departmentList] = await Promise.all([
    request<Record<string, unknown>[]>({ url: '/departments', method: 'GET' }),
    loadSearchableDoctors()
  ]);
  departments.value = departmentList
    .map(toDepartment)
    .filter((item) => item.id && item.name && isPatientSelectableDepartment(item));
  selectedDepartmentId.value = resolveInitialDepartmentId();
  await loadDepartmentResources();
  loadingDepartments.value = false;
}

onLoad((options) => {
  initialDepartmentId.value = decodeURIComponent(String(options?.departmentId || ''));
  initialDoctorId.value = decodeURIComponent(String(options?.doctorId || ''));
  isSearchDoctorEntry.value = String(options?.from || '') === 'doctorSearch' && !!initialDoctorId.value;
});

onMounted(initialize);
onShow(loadCurrentPatientAiConsultation);
</script>

<style scoped>
.booking-page {
  padding: 20rpx 0 0;
  background: var(--patient-theme-page-bg);
}

.search-card {
  margin-bottom: 22rpx;
  padding: 20rpx 24rpx;
  border-radius: 18rpx;
  background: #fff;
  box-shadow: 0 8rpx 22rpx rgba(80, 100, 95, 0.06);
}

.search-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
  height: 72rpx;
  padding: 0 18rpx;
  border-radius: 14rpx;
  background: var(--patient-theme-softest);
  border: 1px solid var(--patient-theme-border);
}

.search-icon {
  width: 24rpx;
  height: 24rpx;
  border: 3rpx solid #b6c2d1;
  border-radius: 50%;
  position: relative;
  flex-shrink: 0;
}

.search-icon::after {
  content: "";
  position: absolute;
  right: -10rpx;
  bottom: -8rpx;
  width: 12rpx;
  height: 3rpx;
  border-radius: 999rpx;
  background: #b6c2d1;
  transform: rotate(45deg);
}

.search-input {
  flex: 1;
  font-size: 28rpx;
  color: #1f2937;
}

.search-placeholder {
  color: #9aa8ba;
}

.search-clear {
  font-size: 34rpx;
  color: #94a3b8;
  padding: 0 8rpx;
}

.search-results {
  margin-top: 16rpx;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.search-result-item {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  padding: 16rpx 18rpx;
  border-radius: 12rpx;
  background: #f8fafb;
}

.result-main {
  display: flex;
  align-items: baseline;
  gap: 10rpx;
}

.result-name {
  color: #111827;
  font-size: 28rpx;
  font-weight: 600;
}

.result-title {
  color: #6b7280;
  font-size: 24rpx;
}

.result-specialty {
  color: #6b7280;
  font-size: 24rpx;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.result-dept {
  color: #94a3b8;
  font-size: 22rpx;
}

.search-empty {
  margin-top: 16rpx;
  padding: 24rpx;
  text-align: center;
  color: #94a3b8;
  font-size: 26rpx;
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
  box-shadow: 0 8rpx 22rpx rgba(80, 100, 95, 0.06);
  border-left: 8rpx solid var(--patient-theme-strong);
}

.ai-title,
.section-title {
  color: #172033;
  font-size: 34rpx;
  font-weight: 600;
}

.ai-desc {
  margin-top: 12rpx;
  color: #334155;
  font-size: 28rpx;
  line-height: 1.55;
}

.ai-meta {
  margin-top: 12rpx;
  color: var(--patient-theme-strong);
  font-size: 26rpx;
}

.chip-scroll {
  margin-top: 20rpx;
}

.single-chip-row {
  display: flex;
  justify-content: center;
  margin-top: 20rpx;
}

.dept-grid {
  margin-top: 20rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
}

.chip-row,
.date-row {
  display: flex;
  gap: 16rpx;
}

.dept-chip {
  padding: 16rpx 26rpx;
  border-radius: 14rpx;
  background: #f1f6fd;
  color: #334155;
  font-size: 27rpx;
  font-weight: 500;
}

.dept-chip.active {
  background: var(--patient-theme-strong);
  color: #fff;
}

.date-strip {
  margin-bottom: 22rpx;
  padding: 20rpx 24rpx;
  border-radius: 18rpx;
  background: #fff;
  box-shadow: 0 8rpx 22rpx rgba(80, 100, 95, 0.06);
  overflow: hidden;
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
  background: var(--patient-theme-strong);
  color: #fff;
}

.date-status {
  margin-top: 8rpx;
  color: var(--patient-theme-strong);
  font-size: 26rpx;
  font-weight: 600;
}

.date-card.active .date-status {
  color: #fff;
}

.doctor-main {
  display: flex;
  gap: 20rpx;
  align-items: center;
}

.avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 88rpx;
  height: 88rpx;
  border-radius: 14rpx;
  background: linear-gradient(135deg, var(--patient-theme-soft) 0%, var(--patient-theme-soft-alt) 100%);
  color: var(--patient-theme-deep);
  font-size: 36rpx;
  font-weight: 600;
}

.doctor-info {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.doctor-line {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
}

.doctor {
  color: #111827;
  font-size: 32rpx;
  font-weight: 600;
  white-space: nowrap;
}

.doctor-title {
  color: #6b7280;
  font-size: 24rpx;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dept-line {
  margin-top: 6rpx;
  color: #6b7280;
  font-size: 26rpx;
  white-space: nowrap;
}

.specialty-line {
  margin-top: 4rpx;
  color: #6b7280;
  font-size: 24rpx;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.price-line,
.slot-price {
  color: #e6821e;
  font-size: 32rpx;
  font-weight: 600;
}

.price-line {
  margin: 10rpx 0;
}

.status-badge {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  min-width: 96rpx;
  overflow: hidden;
  border: 2rpx solid;
  border-radius: 10rpx;
  font-size: 24rpx;
  font-weight: 600;
  text-align: center;
}

.status-badge text {
  padding: 6rpx 10rpx;
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
  margin-top: 14rpx;
  padding-top: 12rpx;
  border-top: 1px solid #edf2f7;
}

.recommend-tag {
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  background: #ecfdf5;
  color: #059669;
  font-size: 22rpx;
  font-weight: 600;
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
  background: linear-gradient(135deg, var(--patient-theme) 0%, var(--patient-theme-strong) 100%);
  color: #fff;
}

.detail-name {
  font-size: 36rpx;
  font-weight: 600;
}

.detail-meta {
  margin-top: 12rpx;
  color: rgba(255, 255, 255, 0.86);
  font-size: 27rpx;
}

.detail-specialty {
  margin-top: 8rpx;
  color: rgba(255, 255, 255, 0.8);
  font-size: 25rpx;
  line-height: 1.4;
}

.level-row {
  display: flex;
  align-items: center;
  padding: 28rpx 30rpx;
  border-bottom: 1px solid #edf2f7;
  color: #1f2937;
  font-size: 31rpx;
  font-weight: 600;
}

.accent-bar {
  display: inline-block;
  width: 8rpx;
  height: 34rpx;
  margin-right: 14rpx;
  border-radius: 999rpx;
  background: var(--patient-theme-strong);
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
  font-weight: 600;
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
  font-weight: 600;
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
  font-weight: 600;
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
  color: var(--patient-theme-strong);
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
  color: var(--patient-theme-strong);
}

.empty-card {
  color: #64748b;
  font-size: 29rpx;
  text-align: center;
}

.empty-title {
  color: #102033;
  font-size: 30rpx;
  font-weight: 600;
  margin-bottom: 10rpx;
}

.empty-desc {
  color: #94a3b8;
  font-size: 26rpx;
}
</style>

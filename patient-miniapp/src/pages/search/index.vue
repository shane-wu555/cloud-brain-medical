<template>
  <patient-nav-bar title="搜索" />
  <view class="page search-page">
    <view class="search-panel">
      <view class="search-row">
        <view class="search-field">
          <view class="search-icon"></view>
          <input
            v-model="keyword"
            always-embed
            class="search-input"
            confirm-type="search"
            focus
            placeholder="搜索科室、医生"
            @input="onKeywordInput"
            @blur="onKeywordBlur"
            @confirm="runSearchFromConfirm"
          />
        </view>
        <view class="search-action" hover-class="search-action-active" @tap="runSearchFromAction">
          搜索
        </view>
      </view>
    </view>

    <view v-if="loading" class="state-card">正在搜索...</view>
    <view v-else-if="searched && !departmentResults.length && !doctorResults.length" class="state-card">
      未找到匹配的科室或医生
    </view>

    <view v-if="departmentResults.length" class="result-section">
      <view class="section-title"><text class="title-bar"></text>门诊科室</view>
      <view
        v-for="department in departmentResults"
        :key="department.id"
        class="result-row"
        @tap="goDepartment(department)"
      >
        <rich-text class="result-text" :nodes="highlight(department.name)"></rich-text>
        <text class="row-arrow">›</text>
      </view>
    </view>

    <view v-if="doctorResults.length" class="result-section">
      <view class="section-title"><text class="title-bar"></text>门诊医生</view>
      <view
        v-for="doctor in doctorResults"
        :key="doctor.id"
        class="result-row"
        @tap="goDoctor(doctor)"
      >
        <rich-text class="result-text" :nodes="doctorLabelNodes(doctor)"></rich-text>
        <text class="row-arrow">›</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { request } from '../../api/http';

interface Department {
  id: string;
  name: string;
}

interface Doctor {
  id: string;
  name: string;
  title: string;
  departmentId: string;
  departmentName: string;
  specialty: string;
}

const keyword = ref('');
const departments = ref<Department[]>([]);
const doctors = ref<Doctor[]>([]);
const departmentResults = ref<Department[]>([]);
const doctorResults = ref<Doctor[]>([]);
const loading = ref(false);
const searched = ref(false);
const EXCLUDED_PATIENT_DEPARTMENT_NAMES = ['检查科', '检验科', '处置室', '药房'];

function normalizeText(value: unknown) {
  if (typeof value === 'string') {
    return value;
  }
  if (typeof value === 'number') {
    return String(value);
  }
  if (value && typeof value === 'object') {
    const record = value as Record<string, unknown>;
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

function toDoctor(item: Record<string, unknown>, department: Department): Doctor {
  return {
    id: normalizeText(item.id),
    name: normalizeText(item.name),
    title: normalizeText(item.title),
    departmentId: normalizeText(item.departmentId) || department.id,
    departmentName: department.name,
    specialty: normalizeText(item.specialty)
  };
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function highlight(value: string) {
  const query = keyword.value.trim();
  const escapedValue = escapeHtml(value);
  if (!query) {
    return escapedValue;
  }

  const index = value.toLocaleLowerCase().indexOf(query.toLocaleLowerCase());
  if (index < 0) {
    return escapedValue;
  }

  const before = escapeHtml(value.slice(0, index));
  const match = escapeHtml(value.slice(index, index + query.length));
  const after = escapeHtml(value.slice(index + query.length));
  return `${before}<span style="color:#2f80ed;">${match}</span>${after}`;
}

function doctorLabelNodes(doctor: Doctor) {
  const title = doctor.title || '门诊医生';
  const dept = doctor.departmentName || '门诊科室';
  return `${highlight(doctor.name)} ${escapeHtml(title)} ${escapeHtml(dept)}`;
}

function normalizeSearchText(value: string) {
  return value.normalize('NFKC').toLocaleLowerCase().replace(/\s+/g, '');
}

function matches(value: string, query: string) {
  return normalizeSearchText(value).includes(normalizeSearchText(query));
}

function isPatientSelectableDepartment(department: Department) {
  const departmentName = normalizeSearchText(department.name);
  return !EXCLUDED_PATIENT_DEPARTMENT_NAMES.some((name) => departmentName.includes(normalizeSearchText(name)));
}

function onKeywordInput(event: { detail: { value?: string } }) {
  const value = event.detail.value ?? '';
  if (keyword.value !== value) {
    keyword.value = value;
  }
}

function onKeywordBlur(event: { detail: { value?: string } }) {
  const value = event.detail.value ?? '';
  if (keyword.value !== value) {
    keyword.value = value;
  }
  searched.value = !!keyword.value.trim();
  applySearch();
}

function applySearch() {
  const query = keyword.value.trim();
  if (!query) {
    departmentResults.value = [];
    doctorResults.value = [];
    return;
  }

  departmentResults.value = departments.value.filter((item) => matches(item.name, query));
  doctorResults.value = doctors.value.filter((item) => matches(item.name, query));
}

function runSearch() {
  searched.value = true;
  applySearch();
}

function runSearchFromAction() {
  runSearch();
}

function runSearchFromConfirm() {
  runSearch();
}

watch(keyword, (value) => {
  searched.value = !!value.trim();
  applySearch();
});

function goDepartment(department: Department) {
  uni.navigateTo({
    url: `/pages/booking/index?departmentId=${encodeURIComponent(department.id)}`
  });
}

function goDoctor(doctor: Doctor) {
  uni.navigateTo({
    url: `/pages/booking/index?departmentId=${encodeURIComponent(doctor.departmentId)}&doctorId=${encodeURIComponent(doctor.id)}&from=doctorSearch`
  });
}

async function loadSearchData() {
  loading.value = true;
  try {
    const departmentList = await request<Record<string, unknown>[]>({ url: '/departments', method: 'GET' });
    departments.value = departmentList
      .map(toDepartment)
      .filter((item) => item.id && item.name && isPatientSelectableDepartment(item));
    applySearch();

    const doctorGroups = await Promise.allSettled(
      departments.value.map(async (department) => {
        const doctorList = await request<Record<string, unknown>[]>({
          url: `/doctors?departmentId=${department.id}`,
          method: 'GET'
        });
        return doctorList.map((item) => toDoctor(item, department));
      })
    );
    doctors.value = doctorGroups
      .filter((result): result is PromiseFulfilledResult<Doctor[]> => result.status === 'fulfilled')
      .flatMap((result) => result.value)
      .filter((item) => item.id && item.name);
    applySearch();
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' });
  } finally {
    loading.value = false;
    applySearch();
  }
}

onMounted(loadSearchData);
</script>

<style scoped>
.search-page {
  min-height: 100vh;
  padding: 0;
  background: #f5f5f5;
}

.search-panel {
  padding: 18rpx 24rpx;
  background: #f5f5f5;
}

.search-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.search-field {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
  height: 72rpx;
  overflow: hidden;
  border: 2rpx solid #9dc4df;
  border-radius: 8rpx;
  background: #fff;
}

.search-icon {
  position: relative;
  flex-shrink: 0;
  width: 34rpx;
  height: 34rpx;
  margin-left: 20rpx;
  border: 4rpx solid #c5cbd2;
  border-radius: 50%;
}

.search-icon::after {
  content: "";
  position: absolute;
  right: -12rpx;
  bottom: -8rpx;
  width: 18rpx;
  height: 4rpx;
  border-radius: 999rpx;
  background: #c5cbd2;
  transform: rotate(45deg);
}

.search-input {
  flex: 1;
  min-width: 0;
  width: 0;
  height: 72rpx;
  padding: 0 20rpx;
  color: #242b36;
  font-size: 32rpx;
}

.search-action {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 112rpx;
  height: 72rpx;
  border-radius: 8rpx;
  background: #2f80ed;
  color: #fff;
  font-size: 32rpx;
  font-weight: 700;
}

.search-action-active {
  background: #1d6ed6;
}

.result-section {
  margin-top: 18rpx;
  background: #fff;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 16rpx;
  height: 92rpx;
  padding: 0 24rpx;
  color: #242b36;
  font-size: 34rpx;
  font-weight: 800;
}

.title-bar {
  width: 8rpx;
  height: 34rpx;
  border-radius: 999rpx;
  background: #2f80ed;
}

.result-row {
  display: flex;
  align-items: center;
  min-height: 98rpx;
  padding: 0 28rpx;
  border-top: 1px solid #edf0f3;
}

.result-text {
  flex: 1;
  min-width: 0;
  color: #252b33;
  font-size: 32rpx;
  line-height: 1.45;
}

.row-arrow {
  flex-shrink: 0;
  margin-left: 18rpx;
  color: #9aa0a6;
  font-size: 60rpx;
  line-height: 1;
}

.state-card {
  margin: 28rpx 24rpx;
  padding: 42rpx 24rpx;
  border-radius: 8rpx;
  background: #fff;
  color: #7b8494;
  font-size: 28rpx;
  text-align: center;
}
</style>

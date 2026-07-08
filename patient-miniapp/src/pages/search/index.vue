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
import { ref, watch } from 'vue';
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

interface SearchResponse {
  departments: Array<Record<string, unknown>>;
  doctors: Array<Record<string, unknown>>;
}

const keyword = ref('');
const departmentResults = ref<Department[]>([]);
const doctorResults = ref<Doctor[]>([]);
const loading = ref(false);
const searched = ref(false);
const highlightColor = '#0899a5';
let searchTimer: ReturnType<typeof setTimeout> | undefined;
let searchSequence = 0;

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

function toDoctor(item: Record<string, unknown>): Doctor {
  return {
    id: normalizeText(item.id),
    name: normalizeText(item.name),
    title: normalizeText(item.title),
    departmentId: normalizeText(item.departmentId),
    departmentName: normalizeText(item.departmentName),
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
  return `${before}<span style="color:${highlightColor};">${match}</span>${after}`;
}

function doctorLabelNodes(doctor: Doctor) {
  const title = doctor.title || '门诊医生';
  const dept = doctor.departmentName || '门诊科室';
  return `${highlight(doctor.name)} ${escapeHtml(title)} ${escapeHtml(dept)}`;
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
  scheduleSearch();
}

function clearSearchResults() {
  departmentResults.value = [];
  doctorResults.value = [];
}

function scheduleSearch() {
  if (searchTimer) {
    clearTimeout(searchTimer);
  }
  searchTimer = setTimeout(() => {
    runSearch();
  }, 250);
}

async function runSearch() {
  if (searchTimer) {
    clearTimeout(searchTimer);
    searchTimer = undefined;
  }
  const query = keyword.value.trim();
  if (!query) {
    searched.value = false;
    loading.value = false;
    clearSearchResults();
    return;
  }

  searched.value = true;
  loading.value = true;
  const sequence = ++searchSequence;
  try {
    const response = await request<SearchResponse>({
      url: `/catalog/patient-search?keyword=${encodeURIComponent(query)}&limit=20`,
      method: 'GET'
    });
    if (sequence !== searchSequence) {
      return;
    }
    departmentResults.value = (response.departments ?? [])
      .map(toDepartment)
      .filter((item) => item.id && item.name);
    doctorResults.value = (response.doctors ?? [])
      .map(toDoctor)
      .filter((item) => item.id && item.name && item.departmentId);
  } catch (error) {
    if (sequence === searchSequence) {
      clearSearchResults();
      uni.showToast({ title: (error as Error).message, icon: 'none' });
    }
  } finally {
    if (sequence === searchSequence) {
      loading.value = false;
    }
  }
}

function runSearchFromAction() {
  runSearch();
}

function runSearchFromConfirm() {
  runSearch();
}

watch(keyword, (value) => {
  searched.value = !!value.trim();
  if (!value.trim()) {
    if (searchTimer) {
      clearTimeout(searchTimer);
    }
    searchSequence++;
    loading.value = false;
    clearSearchResults();
    return;
  }
  scheduleSearch();
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
</script>

<style scoped>
.search-page {
  min-height: 100vh;
  padding: 0;
  background: var(--patient-theme-page-bg);
}

.search-panel {
  padding: 18rpx 24rpx;
  background: var(--patient-theme-page-bg);
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
  border: 2rpx solid var(--patient-theme-border);
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
  background: linear-gradient(135deg, var(--patient-theme) 0%, var(--patient-theme-strong) 100%);
  color: #fff;
  font-size: 32rpx;
  font-weight: 700;
}

.search-action-active {
  background: var(--patient-theme-strong);
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
  background: var(--patient-theme-strong);
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

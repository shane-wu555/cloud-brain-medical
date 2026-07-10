<template>
  <patient-nav-bar title="就诊人管理" />
  <view class="page patient-page">
    <view class="form-card">
      <view class="block-title"><text></text>就诊人信息</view>
      <view v-if="auth.patients.length" class="patient-list">
        <view v-for="patient in auth.patients" :key="patient.id" class="patient-row">
          <view class="patient-top">
            <view class="patient-info">
              <view class="patient-name">{{ patient.name }}</view>
              <view class="patient-meta">{{ patient.idNumber }}</view>
              <view class="patient-meta">{{ genderLabel(patient.gender) }} · {{ patient.birthDate || '未填写出生日期' }}</view>
            </view>
            <text
              :class="['bind-btn', auth.boundPatient?.id === patient.id ? 'bind-btn--bound' : '']"
              @tap="bind(patient.id)"
            >
              {{ auth.boundPatient?.id === patient.id ? '已绑定' : '绑定' }}
            </text>
          </view>
          <view class="patient-actions">
            <view
              :class="['insurance-btn', patient.medicalInsuranceBound ? 'insurance-btn--done' : '']"
              @tap="bindInsurance(patient.id)"
            >
              {{ patient.medicalInsuranceBound ? '已完成医保认证' : '微信医保认证' }}
            </view>
          </view>
        </view>
      </view>

      <view v-else class="empty">暂无就诊人</view>
    </view>

    <view v-if="auth.patients.length < 5" class="form-card">
      <view class="block-title"><text></text>新增就诊人</view>
      <view class="form-row">
        <text class="field-label">姓名<text class="required">*</text></text>
        <input v-model="form.name" class="field-input" placeholder="请填写就诊人姓名" placeholder-class="placeholder" />
      </view>
      <picker :range="idTypeOptions" range-key="label" :value="selectedIdTypeIndex" @change="onIdTypeChange($event)">
        <view class="form-row">
          <text class="field-label">证件类型<text class="required">*</text></text>
          <view class="field-value">{{ idTypeLabel(form.idType) || '选择证件类型' }} <text class="chevron">›</text></view>
        </view>
      </picker>
      <view class="form-row">
        <text class="field-label">证件号码<text class="required">*</text></text>
        <input v-model="form.idNumber" class="field-input" placeholder="请填写就诊人证件号码" placeholder-class="placeholder" />
      </view>
      <picker :range="genderOptions" range-key="label" :value="selectedGenderIndex" @change="onGenderChange($event)">
        <view class="form-row">
          <text class="field-label">性别<text class="required">*</text></text>
          <view class="field-value">{{ genderLabel(form.gender) || '选择性别' }} <text class="chevron">›</text></view>
        </view>
      </picker>
      <view class="form-row birth-row">
        <text class="field-label">出生日期<text class="required">*</text></text>
        <view class="birth-inputs">
          <input v-model="form.birthYear" class="birth-input year-input" type="number" maxlength="4" placeholder="年" placeholder-class="placeholder" />
          <text>年</text>
          <input v-model="form.birthMonth" class="birth-input" type="number" maxlength="2" placeholder="月" placeholder-class="placeholder" />
          <text>月</text>
          <input v-model="form.birthDay" class="birth-input" type="number" maxlength="2" placeholder="日" placeholder-class="placeholder" />
          <text>日</text>
        </view>
      </view>
      <button class="submit-button" :disabled="loading" @tap="submit()">
        {{ loading ? '提交中...' : '添加就诊人' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { onLoad, onShow } from '@dcloudio/uni-app';
import { useAuthStore } from '../../stores/auth';

const auth = useAuthStore();
const loading = ref(false);

const idTypeOptions = [
  { label: '居民身份证', value: 'ID_CARD' },
  { label: '护照', value: 'PASSPORT' },
  { label: '港澳台证件', value: 'HK_MACAO_TAIWAN' },
  { label: '其他证件', value: 'OTHER' }
];

const genderOptions = [
  { label: '男', value: 'MALE' },
  { label: '女', value: 'FEMALE' },
  { label: '未知', value: 'UNKNOWN' }
];

const form = reactive({
  name: '',
  idType: 'ID_CARD',
  idNumber: '',
  gender: 'MALE',
  birthYear: '',
  birthMonth: '',
  birthDay: ''
});

const selectedIdTypeIndex = computed(() => optionIndex(idTypeOptions, form.idType));
const selectedGenderIndex = computed(() => optionIndex(genderOptions, form.gender));

watch(
  () => [form.idType, form.idNumber] as const,
  () => {
    fillBirthDateFromIdCard();
  }
);

onLoad((options) => {
  if (options?.prompt === 'needPatient') {
    setTimeout(() => {
      uni.showToast({ title: '请先添加并绑定就诊人', icon: 'none', duration: 3000 });
    }, 300);
  }
});

onShow(async () => {
  await auth.loadProfile();
});

function idTypeLabel(value?: string) {
  return idTypeOptions.find((item) => item.value === value)?.label || '';
}

function genderLabel(value?: string) {
  return genderOptions.find((item) => item.value === value)?.label || '';
}

function optionIndex(options: Array<{ value: string }>, value: string) {
  const index = options.findIndex((item) => item.value === value);
  return index >= 0 ? index : 0;
}

function onIdTypeChange(event: { detail: { value: string } }) {
  form.idType = idTypeOptions[Number(event.detail.value)]?.value || 'ID_CARD';
  fillBirthDateFromIdCard();
}

function onGenderChange(event: { detail: { value: string } }) {
  form.gender = genderOptions[Number(event.detail.value)]?.value || 'UNKNOWN';
}

async function bind(patientId: string) {
  try {
    await auth.bindPatient(patientId);
    uni.showToast({ title: '已切换就诊人', icon: 'success' });
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' });
  }
}

async function submit() {
  fillBirthDateFromIdCard();
  const birthDate = buildBirthDate();
  if (!form.name.trim() || !form.idNumber.trim() || !birthDate) {
    uni.showToast({ title: '请完整填写就诊人信息', icon: 'none' });
    return;
  }
  const idNumber = form.idNumber.trim();
  if (form.idType === 'ID_CARD' && !/^\d{17}[\dXx]$/.test(idNumber)) {
    uni.showToast({ title: '身份证号格式不正确', icon: 'none' });
    return;
  }
  if (form.idType !== 'ID_CARD' && idNumber.length > 64) {
    uni.showToast({ title: '证件号码最多64位', icon: 'none' });
    return;
  }
  loading.value = true;
  try {
    await auth.addPatient({
      name: form.name.trim(),
      idType: form.idType,
      idNumber,
      gender: form.gender,
      birthDate
    });
    form.name = '';
    form.idNumber = '';
    form.birthYear = '';
    form.birthMonth = '';
    form.birthDay = '';
    uni.showToast({ title: '已添加就诊人', icon: 'success' });
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' });
  } finally {
    loading.value = false;
  }
}

function buildBirthDate() {
  const year = Number(form.birthYear);
  const month = Number(form.birthMonth);
  const day = Number(form.birthDay);
  if (!year || !month || !day || year < 1900 || month < 1 || month > 12 || day < 1 || day > 31) {
    return '';
  }
  const date = new Date(year, month - 1, day);
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
    return '';
  }
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

async function bindInsurance(patientId: string) {
  try {
    const patient = auth.patients.find((item) => item.id === patientId);
    if (patient?.medicalInsuranceBound) {
      uni.showToast({ title: '该就诊人已完成医保认证', icon: 'none' });
      return;
    }
    await auth.bindMedicalInsurance(patientId);
    uni.showToast({ title: '微信医保认证成功', icon: 'none', duration: 2200 });
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' });
  }
}

function fillBirthDateFromIdCard() {
  const idNumber = form.idNumber.trim().toUpperCase();
  if (form.idType !== 'ID_CARD' || !/^\d{17}[\dX]$/.test(idNumber)) {
    return;
  }
  const year = Number(idNumber.slice(6, 10));
  const month = Number(idNumber.slice(10, 12));
  const day = Number(idNumber.slice(12, 14));
  const date = new Date(year, month - 1, day);
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
    return;
  }
  form.birthYear = String(year);
  form.birthMonth = String(month).padStart(2, '0');
  form.birthDay = String(day).padStart(2, '0');
}
</script>

<style scoped>
.patient-page {
  position: relative;
  padding-top: 0;
  padding-bottom: 72rpx;
  background: var(--patient-theme-page-bg);
}

.form-card {
  margin: 28rpx 0 0;
  padding: 26rpx 30rpx 30rpx;
  border-radius: 20rpx;
  background: #fff;
  box-shadow: 0 8rpx 26rpx rgba(31, 84, 140, 0.06);
}

.block-title {
  display: flex;
  align-items: center;
  gap: 14rpx;
  padding: 0 0 20rpx;
  color: var(--patient-theme-strong);
  font-size: 34rpx;
  font-weight: 800;
}

.block-title text {
  width: 8rpx;
  height: 36rpx;
  border-radius: 999rpx;
  background: var(--patient-theme-strong);
}

.patient-list {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.patient-row {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  padding: 24rpx 26rpx;
  border-radius: 16rpx;
  background: var(--patient-theme-softest);
  border: 1px solid var(--patient-theme-border);
}

.patient-top {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18rpx;
}

.patient-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.patient-name {
  color: #0d3d5c;
  font-size: 34rpx;
  font-weight: 700;
  letter-spacing: 0.5rpx;
}

.patient-meta {
  color: #5d6f82;
  font-size: 27rpx;
  line-height: 1.5;
  letter-spacing: 0.3rpx;
}

.patient-actions {
  display: flex;
  flex-direction: row;
  width: 100%;
}

.bind-btn {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 110rpx;
  height: 56rpx;
  padding: 0 22rpx;
  border-radius: 12rpx;
  background: var(--patient-theme-soft);
  color: var(--patient-theme-strong);
  font-size: 26rpx;
  font-weight: 700;
  letter-spacing: 0.5rpx;
  box-shadow: 0 4rpx 10rpx rgba(8, 153, 165, 0.08);
}

.bind-btn--bound {
  background: #f1f5f9;
  color: #64748b;
}

.insurance-btn {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 66rpx;
  padding: 14rpx 24rpx;
  box-sizing: border-box;
  border-radius: 14rpx;
  background: #ecfdf5;
  color: #0f766e;
  font-size: 27rpx;
  font-weight: 700;
  line-height: 1.4;
  letter-spacing: 0.5rpx;
  text-align: center;
  word-break: break-all;
}

.insurance-btn--done {
  background: #f1f5f9;
  color: #64748b;
}

.empty {
  padding: 20rpx 0 10rpx;
  color: #64748b;
  font-size: 28rpx;
}

.form-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 96rpx;
  border-bottom: 1px solid #eef2f6;
}

.form-row:last-of-type {
  border-bottom: none;
}

.field-label {
  flex-shrink: 0;
  color: #2f3542;
  font-size: 31rpx;
  font-weight: 600;
}

.required {
  color: #d71920;
}

.field-input {
  flex: 1;
  min-width: 0;
  color: #1f2937;
  font-size: 30rpx;
  text-align: right;
}

.placeholder {
  color: #c7ccd4;
}

.field-value {
  color: #2f3542;
  font-size: 30rpx;
}

.birth-row {
  gap: 18rpx;
}

.birth-inputs {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8rpx;
  flex: 1;
  min-width: 0;
  color: #2f3542;
  font-size: 29rpx;
}

.birth-input {
  width: 76rpx;
  height: 60rpx;
  border-radius: 10rpx;
  background: var(--patient-theme-softest);
  color: #1f2937;
  font-size: 29rpx;
  text-align: center;
}

.year-input {
  width: 114rpx;
}

.chevron {
  margin-left: 10rpx;
  color: #a9b2bf;
  font-size: 46rpx;
  vertical-align: -4rpx;
}

.submit-button {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 86rpx;
  margin: 30rpx 0 12rpx;
  border-radius: 14rpx;
  background: linear-gradient(135deg, var(--patient-theme) 0%, var(--patient-theme-strong) 100%);
  color: #fff;
  font-size: 32rpx;
  font-weight: 800;
  line-height: 86rpx;
}
</style>

<template>
  <view class="page">
    <view class="card">
      <view class="title">就诊人管理</view>
      <view class="muted">同一账号最多添加 5 个就诊人，业务操作会使用当前绑定的就诊人。</view>

      <view v-if="auth.patients.length" class="patient-list">
        <view v-for="patient in auth.patients" :key="patient.id" class="patient-row">
          <view>
            <view class="patient-name">{{ patient.name }}</view>
            <view class="muted">{{ idTypeLabel(patient.idType) }} {{ patient.idNumber }}</view>
            <view class="muted">{{ genderLabel(patient.gender) }} · {{ patient.birthDate || '未填出生日期' }}</view>
          </view>
          <text
            class="mini-action"
            :disabled="auth.boundPatient?.id === patient.id"
            @tap="bind(patient.id)"
          >
            {{ auth.boundPatient?.id === patient.id ? '已绑定' : '绑定' }}
          </text>
        </view>
      </view>

      <view v-else class="empty">暂无就诊人</view>
    </view>

    <view v-if="auth.patients.length < 5" class="card">
      <view class="title">添加就诊人</view>
      <input v-model="form.name" class="input" placeholder="姓名" />
      <picker :range="idTypeOptions" range-key="label" :value="selectedIdTypeIndex" @change="onIdTypeChange($event)">
        <view class="input">{{ idTypeLabel(form.idType) || '选择证件类型' }}</view>
      </picker>
      <input v-model="form.idNumber" class="input" placeholder="证件号码" />
      <picker :range="genderOptions" range-key="label" :value="selectedGenderIndex" @change="onGenderChange($event)">
        <view class="input">{{ genderLabel(form.gender) || '选择性别' }}</view>
      </picker>
      <picker mode="date" :value="form.birthDate" @change="onBirthDateChange($event)">
        <view class="input">{{ form.birthDate || '选择出生日期' }}</view>
      </picker>
      <button class="button" :disabled="loading" @tap="submit()">
        {{ loading ? '提交中...' : '添加就诊人' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
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
  birthDate: ''
});

const selectedIdTypeIndex = computed(() => optionIndex(idTypeOptions, form.idType));
const selectedGenderIndex = computed(() => optionIndex(genderOptions, form.gender));

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
}

function onGenderChange(event: { detail: { value: string } }) {
  form.gender = genderOptions[Number(event.detail.value)]?.value || 'UNKNOWN';
}

function onBirthDateChange(event: { detail: { value: string } }) {
  form.birthDate = event.detail.value;
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
  if (!form.name.trim() || !form.idNumber.trim() || !form.birthDate) {
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
      birthDate: form.birthDate
    });
    form.name = '';
    form.idNumber = '';
    form.birthDate = '';
    uni.showToast({ title: '已添加就诊人', icon: 'success' });
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' });
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.patient-list {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
  margin-top: 24rpx;
}

.patient-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20rpx;
  padding: 20rpx 0;
  border-bottom: 1px solid #e2e8f0;
}

.patient-name {
  color: #0f172a;
  font-size: 32rpx;
  font-weight: 700;
}

.mini-action {
  display: inline-block;
  min-width: 120rpx;
  padding: 14rpx 20rpx;
  border-radius: 999rpx;
  background: #f8fafc;
  color: #0f766e;
  font-size: 24rpx;
  text-align: center;
}

.empty {
  margin-top: 24rpx;
  color: #64748b;
  font-size: 28rpx;
}
</style>

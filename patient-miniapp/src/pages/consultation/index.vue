<template><view class="page"><view class="card"><view class="title">AI 智能问诊</view><textarea v-model="description" class="input" placeholder="请描述症状和持续时间" /><button class="button" @click="consult">提交问诊</button><view v-if="result" class="result">{{ result }}</view></view></view></template>
<script setup lang="ts">
import { ref } from 'vue';
import { request } from '../../api/http';
const description = ref('');
const result = ref('');
async function consult() { const data = await request<{ recordDraft: string }>({ url: '/ai/consultations', method: 'POST', data: { description: description.value, symptomTags: [] } }); result.value = data.recordDraft; }
</script>
<style scoped>.result { margin-top: 24rpx; line-height: 1.7; }</style>

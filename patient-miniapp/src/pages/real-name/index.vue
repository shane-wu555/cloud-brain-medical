<template>
  <view class="page"><view class="card">
    <view class="title">患者实名认证</view>
    <view class="muted">认证后方可挂号、缴费和查看完整病历</view>
    <input v-model="name" class="input" placeholder="真实姓名" />
    <input v-model="idCard" class="input" maxlength="18" placeholder="18 位身份证号" />
    <button class="button" :loading="loading" @click="submit">提交认证</button>
  </view></view>
</template>
<script setup lang="ts">
import { ref } from 'vue';
import { useAuthStore } from '../../stores/auth';
const auth=useAuthStore(); const name=ref(auth.user?.name??''); const idCard=ref(''); const loading=ref(false);
async function submit(){loading.value=true;try{await auth.verifyRealName(name.value,idCard.value);uni.showToast({title:'认证成功'});setTimeout(()=>uni.navigateBack(),500)}catch(e){uni.showToast({title:(e as Error).message,icon:'none'})}finally{loading.value=false}}
</script>

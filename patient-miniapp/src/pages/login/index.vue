<template>
  <view class="page"><view class="card">
    <view class="title">患者微信小程序</view>
    <view class="muted">注册、登录后进行智能问诊和挂号</view>
    <view class="tabs">
      <text v-for="item in modes" :key="item.value" :class="{ active: mode===item.value }" @click="mode=item.value">{{ item.label }}</text>
    </view>
    <input v-model="phone" class="input" type="number" placeholder="手机号" />
    <input v-if="mode==='PASSWORD' || mode==='REGISTER' || mode==='RESET'" v-model="password" class="input" password placeholder="8-72 位字母和数字密码" />
    <input v-if="mode==='REGISTER'" v-model="name" class="input" placeholder="姓名" />
    <view v-if="mode!=='PASSWORD'" class="code-row">
      <input v-model="smsCode" class="input code-input" type="number" placeholder="短信验证码" />
      <button size="mini" :disabled="countdown>0" @click="sendCode">{{ countdown>0 ? `${countdown}s` : '获取验证码' }}</button>
    </view>
    <view v-if="devCode" class="muted">开发环境验证码：{{ devCode }}</view>
    <button class="button" :loading="loading" @click="submit">{{ submitText }}</button>
  </view></view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useAuthStore } from '../../stores/auth';
type Mode='PASSWORD'|'SMS'|'REGISTER'|'RESET';
const auth=useAuthStore(); const mode=ref<Mode>('PASSWORD'); const phone=ref('13800000000');
const password=ref('abc12345'); const name=ref('新患者'); const smsCode=ref('');
const devCode=ref(''); const countdown=ref(0); const loading=ref(false);
const modes=[{value:'PASSWORD' as Mode,label:'密码登录'},{value:'SMS' as Mode,label:'验证码登录'},{value:'REGISTER' as Mode,label:'注册'},{value:'RESET' as Mode,label:'找回密码'}];
const submitText=computed(()=>({PASSWORD:'登录',SMS:'登录',REGISTER:'注册',RESET:'重置密码'}[mode.value]));
async function sendCode(){
  try{const purpose=mode.value==='REGISTER'?'REGISTER':mode.value==='RESET'?'RESET_PASSWORD':'LOGIN';const result=await auth.sendCode(phone.value,purpose);devCode.value=result.devCode??'';if(result.devCode)smsCode.value=result.devCode;countdown.value=60;const timer=setInterval(()=>{countdown.value--;if(countdown.value<=0)clearInterval(timer)},1000)}
  catch(e){uni.showToast({title:(e as Error).message,icon:'none'})}
}
async function submit(){loading.value=true;try{
  if(mode.value==='PASSWORD')await auth.login(phone.value==='13800000000'?'patient':phone.value,password.value);
  if(mode.value==='SMS')await auth.smsLogin(phone.value,smsCode.value);
  if(mode.value==='REGISTER')await auth.register(phone.value,password.value,name.value,smsCode.value);
  if(mode.value==='RESET'){await auth.resetPassword(phone.value,smsCode.value,password.value);mode.value='PASSWORD';uni.showToast({title:'密码已重置'});return}
  uni.reLaunch({url:'/pages/home/index'});
}catch(e){uni.showToast({title:(e as Error).message,icon:'none'})}finally{loading.value=false}}
</script>

<style scoped>
.tabs{display:flex;flex-wrap:wrap;gap:20rpx;margin:28rpx 0}.tabs text{color:#64748b}.tabs .active{color:#0f766e;font-weight:700}
.code-row{display:flex;align-items:center;gap:12rpx}.code-input{flex:1}.code-row button{margin:0}
</style>

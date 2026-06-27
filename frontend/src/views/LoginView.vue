<template>
  <main class="login-page">
    <section class="login-panel">
      <div>
        <p class="eyebrow">Cloud Brain Medical</p>
        <h1>智慧云脑诊疗平台</h1>
        <p class="muted">医院工作人员 PC Web。患者请使用微信小程序。</p>
      </div>

      <el-form label-position="top" @submit.prevent="handleLogin">
        <el-form-item label="账号">
          <el-select v-model="username" class="full">
            <el-option label="窗口收费 cashier" value="cashier" />
            <el-option label="门诊医生 D0001" value="D0001" />
            <el-option label="检查医生 D0002" value="D0002" />
            <el-option label="检验医生 L0001" value="L0001" />
            <el-option label="处置医生 T0001" value="T0001" />
            <el-option label="药房医生 P0001" value="P0001" />
            <el-option label="管理员 admin" value="admin" />
          </el-select>
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" show-password />
        </el-form-item>
        <el-button type="primary" class="full" :loading="loading" @click="handleLogin">登录</el-button>
      </el-form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '../store/auth';

const router = useRouter();
const auth = useAuthStore();
const username = ref('D0001');
const password = ref('abc12345');
const loading = ref(false);

async function handleLogin() {
  loading.value = true;
  try {
    const path = await auth.signIn(username.value, password.value);
    ElMessage.success('登录成功');
    router.push(path);
  } catch (error) {
    const responseMessage =
      typeof error === 'object' &&
      error !== null &&
      'response' in error &&
      typeof (error as { response?: { data?: { message?: string } } }).response?.data?.message === 'string'
        ? (error as { response?: { data?: { message?: string } } }).response?.data?.message
        : '';
    auth.signOut();
    ElMessage.error(responseMessage || '登录失败，请检查账号和密码');
  } finally {
    loading.value = false;
  }
}
</script>

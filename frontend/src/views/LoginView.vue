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
            <el-option label="门诊医生 doctor" value="doctor" />
            <el-option label="检查医生 check-doctor" value="check-doctor" />
            <el-option label="检验医生 lab-doctor" value="lab-doctor" />
            <el-option label="处置医生 disposal-doctor" value="disposal-doctor" />
            <el-option label="药房医生 pharmacy-doctor" value="pharmacy-doctor" />
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
const username = ref('doctor');
const password = ref('abc12345');
const loading = ref(false);

async function handleLogin() {
  loading.value = true;
  try {
    const path = await auth.signIn(username.value, password.value);
    ElMessage.success('登录成功');
    router.push(path);
  } finally {
    loading.value = false;
  }
}

</script>

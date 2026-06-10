<template>
  <main class="login-page">
    <section class="login-panel">
      <div>
        <p class="eyebrow">Cloud Brain Medical</p>
        <h1>智慧云脑诊疗平台</h1>
        <p class="muted">选择一个演示账号进入第一阶段闭环。</p>
      </div>

      <el-tabs v-model="mode">
        <el-tab-pane label="登录" name="login" />
        <el-tab-pane label="患者注册" name="register" />
      </el-tabs>

      <el-form v-if="mode === 'login'" label-position="top" @submit.prevent="handleLogin">
        <el-form-item label="账号">
          <el-select v-model="username" class="full">
            <el-option label="患者端 patient" value="patient" />
            <el-option label="医生端 doctor" value="doctor" />
            <el-option label="管理端 admin" value="admin" />
          </el-select>
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" show-password />
        </el-form-item>
        <el-button type="primary" class="full" :loading="loading" @click="handleLogin">登录</el-button>
      </el-form>

      <el-form v-else label-position="top" @submit.prevent="handleRegister">
        <el-form-item label="姓名">
          <el-input v-model="registerForm.name" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="registerForm.phone" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="registerForm.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="短信验证码">
          <el-input v-model="registerForm.smsCode" />
        </el-form-item>
        <el-button type="primary" class="full" :loading="loading" @click="handleRegister">注册并登录</el-button>
      </el-form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '../store/auth';
import { registerPatient } from '../api/auth';

const router = useRouter();
const auth = useAuthStore();
const mode = ref('login');
const username = ref('patient');
const password = ref('123456');
const loading = ref(false);
const registerForm = ref({
  name: '新患者',
  phone: '13600000000',
  password: 'abc12345',
  smsCode: '000000'
});

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

async function handleRegister() {
  loading.value = true;
  try {
    const result = await registerPatient(
      registerForm.value.phone,
      registerForm.value.password,
      registerForm.value.name,
      registerForm.value.smsCode
    );
    auth.token = result.token;
    auth.user = result.user;
    localStorage.setItem('access_token', result.token);
    localStorage.setItem('current_user', JSON.stringify(result.user));
    ElMessage.success('注册成功，请后续完成实名认证');
    router.push(auth.homePath);
  } finally {
    loading.value = false;
  }
}
</script>

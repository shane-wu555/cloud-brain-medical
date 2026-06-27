<template>
  <div class="login-page">
    <div class="login-right">
      <div class="login-card">
        <div class="card-header">
          <span class="card-brand">Cloud Brain Medical</span>
          <h2 class="card-title">智慧云脑诊疗平台</h2>
          <div class="card-divider"></div>
        </div>

        <el-form label-position="top" @submit.prevent="handleLogin">
          <el-form-item label="工号">
            <el-input
              v-model="username"
              placeholder="请输入8位工号"
              :prefix-icon="User"
              clearable
            />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>

          <div class="form-extras">
            <el-checkbox v-model="rememberMe">记住账号</el-checkbox>
            <a class="forgot-link" href="#">忘记密码？</a>
          </div>

          <el-button
            class="login-btn"
            :loading="loading"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { User, Lock } from '@element-plus/icons-vue';
import { useAuthStore } from '../store/auth';

const router = useRouter();
const auth = useAuthStore();

const username = ref('');
const password = ref('');
const rememberMe = ref(false);
const loading = ref(false);

onMounted(() => {
  const saved = localStorage.getItem('remembered_username');
  if (saved) {
    username.value = saved;
    rememberMe.value = true;
  }
});

async function handleLogin() {
  if (!username.value.trim()) {
    ElMessage.warning('请输入工号');
    return;
  }
  if (!password.value) {
    ElMessage.warning('请输入密码');
    return;
  }
  loading.value = true;
  try {
    if (rememberMe.value) {
      localStorage.setItem('remembered_username', username.value.trim());
    } else {
      localStorage.removeItem('remembered_username');
    }
    const path = await auth.signIn(username.value.trim(), password.value);
    ElMessage.success('登录成功');
    router.push(path);
  } catch (error) {
    const msg =
      typeof error === 'object' &&
      error !== null &&
      'response' in error
        ? (error as { response?: { data?: { message?: string } } }).response?.data?.message
        : '';
    auth.signOut();
    ElMessage.error(msg || '登录失败，请检查工号和密码');
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding-right: 8%;
  background-image: url('/login-bg.jpg');
  background-size: cover;
  background-position: center;
  background-color: #c8e8f6;
}

.login-right {
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-card {
  width: 400px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 16px;
  padding: 44px 40px 36px;
  box-shadow:
    0 24px 64px rgba(10, 60, 100, 0.14),
    0 4px 16px rgba(10, 60, 100, 0.08);
}

.card-header {
  margin-bottom: 28px;
}

.card-brand {
  display: block;
  font-size: 14px;
  font-weight: 700;
  color: #0cbdcc;
  margin-bottom: 6px;
  letter-spacing: 0.5px;
}

.card-title {
  margin: 0 0 16px;
  font-size: 26px;
  font-weight: 700;
  color: #0d3d5c;
  letter-spacing: 1px;
}

.card-divider {
  height: 3px;
  width: 40px;
  background: linear-gradient(90deg, #0cbdcc, #0899a5);
  border-radius: 2px;
}

.form-extras {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 4px 0 20px;
}

.forgot-link {
  font-size: 13px;
  color: #0cbdcc;
  text-decoration: none;
  transition: opacity 0.2s;
}
.forgot-link:hover { opacity: 0.75; }

.login-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 6px;
  color: #fff !important;
  background: linear-gradient(135deg, #0cbdcc 0%, #0899a5 100%) !important;
  border: none !important;
  border-radius: 8px;
  transition: box-shadow 0.2s, transform 0.2s;
}
.login-btn:hover {
  box-shadow: 0 8px 24px rgba(12, 189, 204, 0.4);
  transform: translateY(-1px);
}
.login-btn:active { transform: translateY(0); }

:deep(.el-form-item__label) {
  color: #374151;
  font-weight: 500;
}

:deep(.el-input__wrapper) {
  border-radius: 8px;
  box-shadow: 0 0 0 1px #d1d5db;
}

:deep(.el-input__wrapper:hover),
:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #0cbdcc !important;
}

:deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
  background-color: #0cbdcc;
  border-color: #0cbdcc;
}

:deep(.el-checkbox__label) {
  color: #6b7280;
  font-size: 13px;
}
</style>

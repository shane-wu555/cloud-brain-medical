<template>
  <div class="audit-screen">
    <header class="audit-nav">
      <div class="audit-nav__brand">
        <span class="audit-nav__logo">+</span>
        <strong>管理员审计台</strong>
      </div>
      <div class="audit-nav__right">
        <span>{{ auth.user?.name }} 管理员</span>
        <span>{{ today }} {{ dayOfWeek }}</span>
        <button class="audit-entry audit-entry--active" type="button">审计</button>
        <el-button size="small" text class="audit-nav__button" @click="router.push('/admin')">返回工作台</el-button>
        <el-button size="small" text class="audit-nav__button" @click="logout">退出</el-button>
      </div>
    </header>

    <main class="audit-main">
      <AdminAuditPanel :active="true" />
    </main>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';
import AdminAuditPanel from '../../components/AdminAuditPanel.vue';
import { useAuthStore } from '../../store/auth';

const router = useRouter();
const auth = useAuthStore();
const weekDays = ['日', '一', '二', '三', '四', '五', '六'];
const today = new Date().toLocaleDateString('zh-CN');
const dayOfWeek = `星期${weekDays[new Date().getDay()]}`;

function logout() {
  auth.signOut();
  router.push('/login');
}
</script>

<style scoped>
.audit-screen {
  min-height: 100vh;
  background: #f3f4f6;
}

.audit-nav {
  height: 52px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(135deg, #0cbdcc 0%, #0899a5 100%);
  color: #fff;
  box-shadow: 0 2px 8px rgb(0 0 0 / 18%);
}

.audit-nav__brand,
.audit-nav__right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.audit-nav__logo {
  width: 30px;
  height: 30px;
  border-radius: 7px;
  display: grid;
  place-items: center;
  background: #fff;
  color: #0899a5;
  font-size: 20px;
  font-weight: 900;
}

.audit-nav__brand strong {
  display: block;
  font-size: 16px;
}

.audit-nav__right {
  gap: 20px;
  font-size: 13px;
  font-family: inherit;
  line-height: 1;
}

.audit-nav__right > span,
.audit-nav__right :deep(.el-button),
.audit-entry {
  height: 32px;
  display: inline-flex;
  align-items: center;
  font: inherit;
  line-height: 1;
}

.audit-nav__button {
  color: rgb(255 255 255 / 88%);
}

.audit-entry {
  padding: 0 14px;
  border: 1px solid rgba(255, 255, 255, 0.38);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  font-weight: 600;
  cursor: default;
}

.audit-entry--active {
  border-color: #fff;
  background: #fff;
  color: #0899a5;
}

.audit-main {
  padding: 16px;
}

@media (max-width: 760px) {
  .audit-nav {
    height: auto;
    padding: 10px 14px;
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }

  .audit-nav__right {
    width: 100%;
    justify-content: flex-end;
    flex-wrap: wrap;
  }
}
</style>

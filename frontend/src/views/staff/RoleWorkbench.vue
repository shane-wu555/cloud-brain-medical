<template>
  <main class="page">
    <div class="workspace">
      <header class="topbar">
        <div>
          <p class="eyebrow">医院工作人员 PC Web</p>
          <h1>{{ title }}</h1>
          <p class="muted">{{ description }}</p>
        </div>
        <el-button @click="logout">退出</el-button>
      </header>
      <el-alert
        title="工作台权限已经按最终角色拆分"
        description="后续业务列表只会展示当前角色有权处理的医嘱和患者数据。"
        type="success"
        :closable="false"
        show-icon
      />
    </div>
  </main>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';
import { useAuthStore } from '../../store/auth';

defineProps<{ title: string; description: string }>();
const router = useRouter();
const auth = useAuthStore();

function logout() {
  auth.signOut();
  router.push('/login');
}
</script>

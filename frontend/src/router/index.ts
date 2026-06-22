import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../store/auth';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
    { path: '/doctor/outpatient', component: () => import('../views/doctor/DoctorWorkbench.vue'), meta: { roles: ['OUTPATIENT_DOCTOR'] } },
    { path: '/doctor/check', component: () => import('../views/staff/RoleWorkbench.vue'), props: { title: '检查医生工作台', description: '检查登记、影像查看、结果录入与报告确认' }, meta: { roles: ['CHECK_DOCTOR'] } },
    { path: '/doctor/lab', component: () => import('../views/staff/RoleWorkbench.vue'), props: { title: '检验医生工作台', description: '标本登记、检验结果与异常指标复核' }, meta: { roles: ['LAB_DOCTOR'] } },
    { path: '/doctor/disposal', component: () => import('../views/staff/RoleWorkbench.vue'), props: { title: '处置医生工作台', description: '处置登记、执行与结果记录' }, meta: { roles: ['DISPOSAL_DOCTOR'] } },
    { path: '/doctor/pharmacy', component: () => import('../views/staff/RoleWorkbench.vue'), props: { title: '药房医生工作台', description: '处方接收、发药、退药与库存管理' }, meta: { roles: ['PHARMACY_DOCTOR'] } },
    { path: '/cashier', component: () => import('../views/staff/RoleWorkbench.vue'), props: { title: '窗口挂号收费工作台', description: '线下建档、挂号、缴费、退号与退费' }, meta: { roles: ['CASHIER'] } },
    { path: '/admin', component: () => import('../views/admin/AdminDashboard.vue'), meta: { roles: ['ADMIN'] } }
  ]
});

router.beforeEach((to) => {
  const auth = useAuthStore();
  if (to.meta.public) {
    return true;
  }
  if (!auth.isAuthenticated) {
    return '/login';
  }
  const roles = to.meta.roles as string[] | undefined;
  if (roles?.length && !roles.includes(auth.user?.role ?? '')) {
    return auth.homePath;
  }
  return true;
});

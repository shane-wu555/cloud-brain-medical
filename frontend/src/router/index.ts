import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../store/auth';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
    { path: '/doctor/outpatient', component: () => import('../views/doctor/DoctorWorkbench.vue'), meta: { roles: ['OUTPATIENT_DOCTOR'] } },
    { path: '/doctor/check', component: () => import('../views/medical-tech/MedicalTechWorkbench.vue'), meta: { roles: ['CHECK_DOCTOR'] } },
    { path: '/doctor/lab', component: () => import('../views/medical-tech/MedicalTechWorkbench.vue'), meta: { roles: ['LAB_DOCTOR'] } },
    { path: '/doctor/disposal', component: () => import('../views/disposal/DisposalWorkbench.vue'), meta: { roles: ['DISPOSAL_DOCTOR'] } },
    { path: '/doctor/pharmacy', component: () => import('../views/pharmacy/PharmacyWorkbench.vue'), meta: { roles: ['PHARMACY_DOCTOR'] } },
    { path: '/cashier', component: () => import('../views/cashier/CashierWorkbench.vue'), meta: { roles: ['CASHIER'] } },
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

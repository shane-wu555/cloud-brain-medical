import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../store/auth';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/patient' },
    { path: '/login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
    { path: '/patient', component: () => import('../views/patient/PatientHome.vue'), meta: { roles: ['PATIENT'] } },
    { path: '/doctor', component: () => import('../views/doctor/DoctorWorkbench.vue'), meta: { roles: ['DOCTOR'] } },
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

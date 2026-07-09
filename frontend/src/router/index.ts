import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { authState, canAccessRoute, type Role } from '../auth/authStore';
import { financeRoles, membershipRoles, reportRoles, selfServiceRoles, staffRoles } from '../auth/roles';
import LoginView from '../views/LoginView.vue';
import ChangePasswordView from '../views/ChangePasswordView.vue';
import DashboardView from '../views/DashboardView.vue';
import FinanceView from '../views/FinanceView.vue';
import BudgetsView from '../views/BudgetsView.vue';
import MembersView from '../views/MembersView.vue';
import OfferingsView from '../views/OfferingsView.vue';
import ProfileView from '../views/ProfileView.vue';
import ReferenceDataView from '../views/ReferenceDataView.vue';

const protectedPlaceholder = {
  template: '<section><h2>{{ title }}</h2></section>',
  props: ['title'],
};

const routes: RouteRecordRaw[] = [
  { path: '/login', component: LoginView },
  { path: '/change-password', component: ChangePasswordView },
  { path: '/', component: DashboardView, meta: { roles: staffRoles } },
  { path: '/members', component: MembersView, meta: { roles: membershipRoles } },
  { path: '/offerings', component: OfferingsView, meta: { roles: financeRoles } },
  { path: '/finance', component: FinanceView, meta: { roles: financeRoles } },
  { path: '/budgets', component: BudgetsView, meta: { roles: financeRoles } },
  { path: '/reference-data', component: ReferenceDataView, meta: { roles: ['ADMIN', 'TREASURER', 'MEMBERSHIP'] as Role[] } },
  { path: '/reports', component: protectedPlaceholder, props: { title: 'Reports' }, meta: { roles: reportRoles } },
  { path: '/profile', component: ProfileView, meta: { roles: selfServiceRoles } },
];

export const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach((to) => {
  if (to.path === '/login') {
    return true;
  }

  if (!authState.currentUser) {
    return '/login';
  }

  if (authState.currentUser.mustChangePassword && to.path !== '/change-password') {
    return '/change-password';
  }

  const allowedRoles = to.meta.roles as Role[] | undefined;
  if (allowedRoles && !canAccessRoute(authState.currentUser.roles, allowedRoles)) {
    return '/';
  }

  return true;
});

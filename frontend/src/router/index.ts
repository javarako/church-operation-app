import { createRouter, createWebHistory, type RouteRecordRaw, type RouterHistory } from 'vue-router';
import { authState, canAccessRoute, type Role } from '../auth/authStore';
import { adminRoles, financeRoles, membershipRoles, reportRoles, selfServiceRoles, staffRoles } from '../auth/roles';
import LoginView from '../views/LoginView.vue';
import ChangePasswordView from '../views/ChangePasswordView.vue';
import ForgotPasswordView from '../views/ForgotPasswordView.vue';
import ResetPasswordView from '../views/ResetPasswordView.vue';
import DashboardView from '../views/DashboardView.vue';
import FinanceView from '../views/FinanceView.vue';
import BudgetsView from '../views/BudgetsView.vue';
import MembersView from '../views/MembersView.vue';
import OfferingsView from '../views/OfferingsView.vue';
import ProfileView from '../views/ProfileView.vue';
import ReferenceDataView from '../views/ReferenceDataView.vue';
import ReportsView from '../views/ReportsView.vue';
import SystemAdministrationView from '../views/SystemAdministrationView.vue';

export const routes: RouteRecordRaw[] = [
  { path: '/login', component: LoginView },
  { path: '/change-password', component: ChangePasswordView },
  { path: '/forgot-password', component: ForgotPasswordView },
  { path: '/reset-password', component: ResetPasswordView },
  { path: '/', component: DashboardView, meta: { roles: staffRoles } },
  { path: '/members', component: MembersView, meta: { roles: membershipRoles } },
  { path: '/offerings', component: OfferingsView, meta: { roles: financeRoles } },
  { path: '/finance', component: FinanceView, meta: { roles: financeRoles } },
  { path: '/budgets', component: BudgetsView, meta: { roles: financeRoles } },
  { path: '/reference-data', component: ReferenceDataView, meta: { roles: ['ADMIN', 'TREASURER', 'MEMBERSHIP'] as Role[] } },
  { path: '/reports', component: ReportsView, meta: { roles: reportRoles } },
  { path: '/profile', component: ProfileView, meta: { roles: selfServiceRoles } },
  { path: '/system-administration', component: SystemAdministrationView, meta: { roles: adminRoles } },
];

export function createAppRouter(history: RouterHistory = createWebHistory()) {
  const appRouter = createRouter({ history, routes });

  appRouter.beforeEach((to) => {
    if (['/login', '/forgot-password', '/reset-password'].includes(to.path)) {
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
      return authState.currentUser.roles.includes('MEMBER') ? '/profile' : '/';
    }

    return true;
  });

  return appRouter;
}

export const router = createAppRouter();

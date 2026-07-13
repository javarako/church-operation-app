import { getJson } from './http';

export interface DashboardTrendPoint {
  sunday: string;
  amount: number;
}

export interface DashboardResponse {
  activeMemberCount: number;
  newMemberCount: number;
  ytdOfferingActual: number;
  ytdOfferingBudget: number;
  ytdOfferingPercentage: number | null;
  ytdExpenseActual: number;
  ytdExpenseBudget: number;
  ytdExpensePercentage: number | null;
  pendingChequeCount: number;
  pendingChequeTotal: number;
  weekOfferingTotal: number;
  monthOfferingTotal: number;
  yearOfferingTotal: number;
  fiscalYearStart: string;
  fiscalYearEnd: string;
  offeringTrend: DashboardTrendPoint[];
}

export function getDashboard() {
  return getJson<DashboardResponse>('/api/dashboard');
}

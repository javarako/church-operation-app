import { deleteJson, getJson, postJson, putJson } from './http';

export type BudgetType = 'CARRY_OVER' | 'OFFERING_INCOME' | 'EXPENSE';

export interface Budget {
  id: string;
  fiscalYear: number;
  budgetType: BudgetType;
  category?: string;
  subCategory?: string;
  budget: number;
  memo?: string;
}

export interface BudgetPayload {
  fiscalYear: number;
  budgetType: BudgetType;
  category?: string;
  subCategory?: string;
  budget: number;
  memo?: string;
}

export function listBudgets(fiscalYear: number) {
  return getJson<Budget[]>(`/api/budgets?fiscalYear=${encodeURIComponent(String(fiscalYear))}`);
}

export function createBudget(payload: BudgetPayload) {
  return postJson<BudgetPayload, Budget>('/api/budgets', payload);
}

export function updateBudget(id: string, payload: BudgetPayload) {
  return putJson<BudgetPayload, Budget>(`/api/budgets/${id}`, payload);
}

export function deleteBudget(id: string) {
  return deleteJson<Budget>(`/api/budgets/${id}`);
}

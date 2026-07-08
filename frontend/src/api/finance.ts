import { deleteJson, getJson, postJson, putJson } from './http';

export type FinancialTransactionType = 'INCOME' | 'EXPENSE';
export type FinancialSourceType = 'OFFERING' | 'MANUAL';

export interface FinancialTransaction {
  id: string;
  type: FinancialTransactionType;
  transactionDate: string;
  amount: number;
  category: string;
  subCategory?: string;
  hstIncluded: boolean;
  chequeNo?: string;
  chequeCleared: boolean;
  payableTo?: string;
  treasurer?: string;
  memo?: string;
  sourceType: FinancialSourceType;
  sourceId?: string;
}

export interface FinancialTransactionPayload {
  transactionDate: string;
  amount: number;
  category: string;
  subCategory?: string;
  hstIncluded: boolean;
  chequeNo?: string;
  chequeCleared: boolean;
  payableTo?: string;
  treasurer?: string;
  memo?: string;
}

export function listFinanceTransactions() {
  return getJson<FinancialTransaction[]>('/api/finance/transactions');
}

export function createExpense(payload: FinancialTransactionPayload) {
  return postJson<FinancialTransactionPayload, FinancialTransaction>('/api/finance/expenses', payload);
}

export function updateExpense(id: string, payload: FinancialTransactionPayload) {
  return putJson<FinancialTransactionPayload, FinancialTransaction>(`/api/finance/expenses/${id}`, payload);
}

export function deleteExpense(id: string) {
  return deleteJson<FinancialTransaction>(`/api/finance/expenses/${id}`);
}

import { getBlob, getJson, postBlob, postJson } from './http';

export type GivingType = 'MEMBER' | 'ANONYMOUS' | 'GROUP';
export type BudgetType = 'CARRY_OVER' | 'OFFERING_INCOME' | 'EXPENSE';

export interface WeeklyOfferingReportRow {
  offeringSunday: string;
  fundCode: string;
  categoryCode: string;
  givingType: GivingType;
  paymentMethod: string;
  count: number;
  totalAmount: number;
}

export interface MemberOfferingSummaryReportRow {
  memberId: string;
  memberName: string;
  primaryEmail: string;
  offeringNumber?: string;
  fundCode: string;
  categoryCode: string;
  count: number;
  totalAmount: number;
}

export const DEFAULT_THANK_YOU_NOTE = "Thank you for your faithful and generous support over the past year. Because of you, we are able to continue serving our community and sharing God's message.";

export type TaxReceiptStatus = 'ISSUED' | 'VOID';

export interface TaxReceiptSummaryRow {
  memberId: string;
  offeringNumber: string;
  donorName: string;
  donorAddress?: string;
  taxYear: number;
  totalAmount: number;
  receiptId?: string;
  receiptNumber?: string;
  receiptStatus?: TaxReceiptStatus;
  sourceChanged: boolean;
}

export interface TaxReceiptResult {
  id: string;
  receiptNumber: string;
  status: TaxReceiptStatus;
}

export interface FinancialBudgetReportRow {
  fiscalYear: number;
  budgetType: BudgetType;
  category?: string;
  subCategory?: string;
  budget: number;
  actual: number;
  variance: number;
}

interface WeeklyOfferingReportFilters {
  start: string;
  end: string;
  fundCode?: string;
  categoryCode?: string;
  paymentMethod?: string;
}

interface MemberOfferingSummaryReportFilters {
  start: string;
  end: string;
  offeringNumber?: string;
  fundCode?: string;
  categoryCode?: string;
}

interface TaxReceiptSummaryFilters {
  taxYear: number;
  offeringNumber?: string;
}

interface TaxReceiptIssuePayload {
  taxYear: number;
  offeringNumber: string;
  thankYouNote: string;
}

interface TaxReceiptBatchPayload {
  taxYear: number;
  thankYouNote: string;
}

interface FinancialBudgetReportFilters {
  fiscalYear: number;
}

export interface QuarterlyFinancialReportFilters {
  year: number;
  quarter: 1 | 2 | 3 | 4;
}

export interface YearlyFinancialReportFilters {
  fiscalYear: number;
}

function buildQuery(params: Record<string, string | number | undefined>) {
  const search = new URLSearchParams();

  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') {
      search.set(key, String(value));
    }
  });

  return search.toString();
}

function withQuery(path: string, params: Record<string, string | number | undefined>) {
  const query = buildQuery(params);
  return query ? `${path}?${query}` : path;
}

export function listWeeklyOfferingReport(filters: WeeklyOfferingReportFilters) {
  return getJson<WeeklyOfferingReportRow[]>(withQuery('/api/reports/weekly-offerings', { ...filters }));
}

export function listMemberOfferingSummaryReport(filters: MemberOfferingSummaryReportFilters) {
  return getJson<MemberOfferingSummaryReportRow[]>(withQuery('/api/reports/member-offerings', { ...filters }));
}

export function listTaxReceiptSummary(filters: TaxReceiptSummaryFilters) {
  return getJson<TaxReceiptSummaryRow[]>(withQuery('/api/reports/tax-receipts/summary', { ...filters }));
}

export function issueTaxReceipt(payload: TaxReceiptIssuePayload) {
  return postJson<TaxReceiptIssuePayload, TaxReceiptResult>('/api/reports/tax-receipts/issue', payload);
}

export function issueBatchTaxReceipts(payload: TaxReceiptBatchPayload) {
  return postBlob('/api/reports/tax-receipts/issue-batch', payload);
}

export function downloadTaxReceiptPdf(receiptId: string) {
  return getBlob(`/api/reports/tax-receipts/${receiptId}/pdf`);
}

export function voidTaxReceipt(receiptId: string, reason: string) {
  return postJson<{ reason: string }, TaxReceiptResult>(`/api/reports/tax-receipts/${receiptId}/void`, { reason });
}

export function replaceTaxReceipt(receiptId: string, thankYouNote: string) {
  return postJson<{ thankYouNote: string }, TaxReceiptResult>(`/api/reports/tax-receipts/${receiptId}/replace`, {
    thankYouNote,
  });
}

export function listFinancialBudgetReport(filters: FinancialBudgetReportFilters) {
  return getJson<FinancialBudgetReportRow[]>(withQuery('/api/reports/financial-budget', { ...filters }));
}

export function downloadQuarterlyOfferingReport(filters: QuarterlyFinancialReportFilters) {
  return getBlob(withQuery('/api/reports/quarterly-offerings.xlsx', { ...filters }));
}

export function downloadQuarterlyExpenditureReport(filters: QuarterlyFinancialReportFilters) {
  return getBlob(withQuery('/api/reports/quarterly-expenditures.xlsx', { ...filters }));
}

export function downloadYearlyOfferingReport(filters: YearlyFinancialReportFilters) {
  return getBlob(withQuery('/api/reports/yearly-offerings.xlsx', { ...filters }));
}

export function downloadYearlyExpenditureReport(filters: YearlyFinancialReportFilters) {
  return getBlob(withQuery('/api/reports/yearly-expenditures.xlsx', { ...filters }));
}

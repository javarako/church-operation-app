import { getBlob, getBlobResponse, getJson, postBlob, postJson } from './http';

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

export type YearEndReportType = 'OFFERING' | 'EXPENDITURE';
export type YearEndClosingStatus = 'NOT_CLOSED' | 'CLOSED' | 'REOPENED';

export interface YearEndClosingReportStatus {
  reportType: YearEndReportType;
  status: YearEndClosingStatus;
  version?: number;
  eventAt?: string;
}

export interface YearEndClosingStatusResponse {
  fiscalYear: number;
  fiscalEndDate: string;
  closeEligible: boolean;
  offering: YearEndClosingReportStatus;
  expenditure: YearEndClosingReportStatus;
}

export interface YearEndClosingRequest {
  fiscalYear: number;
  currentPassword: string;
}

export interface YearlyWorkbookDownload {
  blob: Blob;
  filename: string;
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

export function getYearEndClosingStatus(filters: YearlyFinancialReportFilters) {
  return getJson<YearEndClosingStatusResponse>(withQuery('/api/reports/yearly-closing-status', { ...filters }));
}

export function closeYearEndReport(reportType: YearEndReportType, request: YearEndClosingRequest) {
  return postJson<YearEndClosingRequest, YearEndClosingReportStatus>(
    `/api/reports/yearly-closing/${reportType}/close`,
    request,
  );
}

export function reopenYearEndReport(reportType: YearEndReportType, request: YearEndClosingRequest) {
  return postJson<YearEndClosingRequest, YearEndClosingReportStatus>(
    `/api/reports/yearly-closing/${reportType}/reopen`,
    request,
  );
}

export async function downloadYearlyOfferingReport(
  filters: YearlyFinancialReportFilters,
): Promise<YearlyWorkbookDownload> {
  const response = await getBlobResponse(withQuery('/api/reports/yearly-offerings.xlsx', { ...filters }));
  return downloadFromResponse(response, `yearly-offerings-${filters.fiscalYear}.xlsx`);
}

export async function downloadYearlyExpenditureReport(
  filters: YearlyFinancialReportFilters,
): Promise<YearlyWorkbookDownload> {
  const response = await getBlobResponse(withQuery('/api/reports/yearly-expenditures.xlsx', { ...filters }));
  return downloadFromResponse(response, `yearly-expenditures-${filters.fiscalYear}.xlsx`);
}

async function downloadFromResponse(response: Response, fallbackFilename: string): Promise<YearlyWorkbookDownload> {
  return {
    blob: await response.blob(),
    filename: attachmentFilename(response.headers.get('Content-Disposition')) ?? fallbackFilename,
  };
}

function attachmentFilename(contentDisposition: string | null) {
  if (!contentDisposition) {
    return undefined;
  }
  const encoded = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  if (encoded) {
    return decodeURIComponent(encoded.trim());
  }
  return contentDisposition.match(/filename="?([^";]+)"?/i)?.[1]?.trim();
}

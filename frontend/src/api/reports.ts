import { getJson } from './http';

export type GivingType = 'MEMBER' | 'ANONYMOUS' | 'GROUP';
export type BudgetType = 'CARRY_OVER' | 'OFFERING_INCOME' | 'EXPENSE';

export interface WeeklyOfferingReportRow {
  offeringSunday: string;
  fundCategory: string;
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
  fundCategory: string;
  count: number;
  totalAmount: number;
}

export interface OfficialTaxReportRow {
  churchName: string;
  churchAddress: string;
  churchContactInfo: string;
  treasurerName: string;
  taxYear: number;
  memberId: string;
  memberName: string;
  primaryEmail: string;
  offeringNumber?: string;
  memberAddress?: string;
  givingDate: string;
  fundCategory: string;
  amount: number;
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
  fundCategory?: string;
  paymentMethod?: string;
}

interface MemberOfferingSummaryReportFilters {
  start: string;
  end: string;
  memberId?: string;
  fundCategory?: string;
}

interface OfficialTaxReportFilters {
  taxYear: number;
  memberId?: string;
}

interface FinancialBudgetReportFilters {
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

export function listOfficialTaxReport(filters: OfficialTaxReportFilters) {
  return getJson<OfficialTaxReportRow[]>(withQuery('/api/reports/tax-return', { ...filters }));
}

export function listFinancialBudgetReport(filters: FinancialBudgetReportFilters) {
  return getJson<FinancialBudgetReportRow[]>(withQuery('/api/reports/financial-budget', { ...filters }));
}

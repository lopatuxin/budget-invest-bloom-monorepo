import type { PayoutKind } from '@/types/investment';

export type NormStatus = 'ABOVE_MUCH' | 'ABOVE' | 'NORMAL' | 'BELOW' | 'NO_HISTORY';

export interface NormComparison {
  usualByDay: number | null;
  averageMonthly: number | null;
  deviationPercent: number | null;
  status: NormStatus;
}

// Category shape as returned by /api/budget/summary
export interface BudgetCategorySummary {
  id: string;
  name: string;
  emoji: string;
  amount: number;
  budget: number;
  percentUsed: number;
  norm: NormComparison;
}

export interface BudgetSummaryResponse {
  period: { month: number; year: number };
  income: number;
  expenses: number;
  balance: number;
  personalInflation: number;
  trends: TrendsData;
  dayOfMonth: number;
  daysInMonth: number;
  expenseNorm: NormComparison;
  incomeNorm: NormComparison;
  categories: BudgetCategorySummary[];
}

export type OperationKind = 'EXPENSE' | 'INCOME';

export interface Operation {
  id: string;
  kind: OperationKind;
  date: string;
  amount: number;
  description: string | null;
  categoryId?: string;
  categoryName?: string;
  categoryEmoji?: string;
  source?: string;
  sourceName?: string;
}

export interface OperationsResponse {
  period: { month: number; year: number };
  total: number;
  items: Operation[];
}

export interface OperationCategory {
  id: string;
  name: string;
  emoji: string;
}

export interface TrendsData {
  income: string;
  expenses: string;
  balance: string;
  inflation: string;
}

// Re-exported from common for backward compatibility
export type { ApiResponse } from './common';

// Shape returned by POST /api/budget/categories/page — the category page.

export interface CategoryPageCategory {
  id: string;
  name: string;
  emoji?: string;
  system: boolean;
}

export interface CategoryMonthAmount {
  month: number;
  year: number;
  amount: number;
  partial: boolean;
}

export interface CategoryPageResponse {
  category: CategoryPageCategory;
  period: { month: number; year: number };
  dayOfMonth: number;
  daysInMonth: number;
  spent: number;
  norm: NormComparison;
  normMonthsCounted: number;
  sharePercent?: number | null;
  operationsCount: number;
  averageCheck?: number | null;
  largestAmount?: number | null;
  // Always 12 entries, in month order (oldest to newest, ending on the requested month).
  months: CategoryMonthAmount[];
  operations: Operation[];
}

export interface CategoryHasExpensesErrorBody {
  code: 'CATEGORY_HAS_EXPENSES';
  expenseCount: number;
}

// Shape returned by POST /api/budget/overview — the capital page.

export interface Change {
  percent?: number | null;
  status: NormStatus;
}

export interface CapitalPoint {
  month: number;
  year: number;
  date: string;
  freeMoney: number;
  portfolioValue: number;
  total: number;
}

export interface CapitalSection {
  total: number;
  freeMoney: number;
  portfolioValue: number;
  yearAgo?: number | null;
  changeAbs?: number | null;
  change: Change;
  history: CapitalPoint[];
  portfolioHistoryPending: boolean;
  // Optional so an old backend response (pre-rollout) still type-checks.
  pricesStale?: boolean;
  staleTickers?: string[];
}

export interface NextDividend {
  ticker: string;
  securityName: string;
  recordDate: string;
  // Present once T-Invest has an announced payment date for this dividend.
  paymentDate: string | null;
  totalAmount: number;
  currency: string;
  kind: PayoutKind;
}

// Everything past `available` and `assetsCount` is missing from the response when the
// portfolio valuation is unavailable — the backend DTOs are @JsonInclude(NON_NULL), so an
// absent field arrives as undefined, not null. Read this section only under `available`.
export interface PortfolioSection {
  available: boolean;
  assetsCount: number;
  value?: number;
  cost?: number;
  pnlAmount?: number;
  pnl?: Change;
  dividends12m?: number;
  nextDividend?: NextDividend | null;
}

export interface SavingsSection {
  rate12m?: number | null;
  currentMonthRate?: number | null;
}

export interface MonthTotals {
  month: number;
  year: number;
  income: number;
  expenses: number;
  saved: number;
  partial: boolean;
}

export interface TotalsLine {
  amount: number;
  previous: number;
  change: Change;
}

export interface Totals12m {
  income: TotalsLine;
  expenses: TotalsLine;
  saved: TotalsLine;
}

export interface OverviewPageResponse {
  asOf: string;
  currentMonth: { month: number; year: number; partial: boolean };
  capital: CapitalSection;
  currentMonthBalance: number;
  portfolio: PortfolioSection;
  savings: SavingsSection;
  months: MonthTotals[];
  totals12m: Totals12m;
  personalInflationPercent?: number | null;
}

// Shape returned by POST /api/budget/analytics — the /analytics page.

export interface AnalyticsMonthExtreme {
  month: number;
  amount: number;
}

export interface AnalyticsMonthPoint {
  month: number;
  // Absent for months after the current one and for every month of a future year.
  current?: number | null;
  // Always a number — 0 when the previous year has no data for the month.
  previous: number;
  partial: boolean;
}

export interface AnalyticsSection {
  total: number;
  previousTotal: number;
  monthsCounted: number;
  previousMonthsCounted: number;
  average?: number | null;
  previousAverage?: number | null;
  change: Change;
  maxMonth?: AnalyticsMonthExtreme | null;
  minMonth?: AnalyticsMonthExtreme | null;
  // Always 12 entries, in month order.
  months: AnalyticsMonthPoint[];
}

export interface AnalyticsCategory {
  categoryId: string;
  categoryName: string;
  emoji: string;
  averageCurrent: number;
  averagePrevious: number;
  change: Change;
  contributionPoints?: number | null;
  sharePercent: number;
}

export interface AnalyticsPageResponse {
  year: number;
  previousYear: number;
  earliestYear?: number | null;
  currentMonth: { month: number; year: number; partial: boolean };
  previousYearHasData: boolean;
  expenses: AnalyticsSection;
  income: AnalyticsSection;
  savings: AnalyticsSection;
  savingsRatePercent?: number | null;
  previousSavingsRatePercent?: number | null;
  personalInflationPercent?: number | null;
  // Populated only on the expenses tab; empty for income/savings.
  categories: AnalyticsCategory[];
}

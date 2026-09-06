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

export interface MonthlyMetric {
  month: number;
  monthName: string;
  amount: number;
}

export interface CategoryInflation {
  categoryId: string;
  categoryName: string;
  emoji: string;
  avgCurrent: number;
  avgPrevious: number;
  changePercent: number;
  contribution: number;
  weightPercent: number;
}

export interface MetricResponse {
  year: number;
  currentValue: number;
  previousValue: number;
  changePercent: string;
  yearlyAverage: number;
  yearlyMax: number;
  monthlyData: MonthlyMetric[];
  categoryBreakdown?: CategoryInflation[];
}

export interface CategoryAnalyticsResponse {
  categoryId: string;
  categoryName: string;
  emoji: string;
  budget: number;
  monthlyData: MonthlyMetric[];
  yearlyData: YearlyMetric[];
  expenses: ExpenseItem[];
  totalExpenses: number;
  totalYear: number;
  averageYear: number;
}

export interface YearlyMetric {
  year: number;
  amount: number;
}

export interface ExpenseItem {
  id: string;
  categoryId: string;
  categoryName: string;
  amount: number;
  description: string;
  date: string;
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
}

export interface NextDividend {
  ticker: string;
  securityName: string;
  paymentDate: string;
  totalAmount: number;
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

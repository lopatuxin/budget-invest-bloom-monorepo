export type NormStatus = 'ABOVE_MUCH' | 'ABOVE' | 'NORMAL' | 'BELOW' | 'NO_HISTORY';

export interface NormComparison {
  usualByDay: number | null;
  averageMonthly: number | null;
  deviationPercent: number | null;
  status: NormStatus;
}

// Category shape as returned by /api/budget/summary — distinct from the plain
// CategorySummary below (used by /api/budget/overview), which has no norm.
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

export interface OverviewTrendsData {
  expenses: string;
  balance: string;
  capital: string;
}

export interface OverviewSummaryResponse {
  period: { month: number; year: number };
  income: number;
  expenses: number;
  balance: number;
  capital: number;
  savingsRate: number;
  trends: OverviewTrendsData;
  categories: CategorySummary[];
}

export interface CategorySummary {
  id: string;
  name: string;
  emoji: string;
  amount: number;
  budget: number;
  percentUsed: number;
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

export interface LifetimeBalanceResponse {
  freeCapital: number;
  totalIncome: number;
  totalExpense: number;
}

export interface CategoryHasExpensesErrorBody {
  code: 'CATEGORY_HAS_EXPENSES';
  expenseCount: number;
}

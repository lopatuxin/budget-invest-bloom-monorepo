export interface BudgetSummaryResponse {
  period: { month: number; year: number };
  income: number;
  expenses: number;
  balance: number;
  capital: number;
  personalInflation: number;
  trends: TrendsData;
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
  capital: string;
  inflation: string;
}

export interface ApiResponse<T> {
  id: string;
  status: number;
  message: string;
  timestamp: string;
  body: T;
}

export interface MonthlyMetric {
  month: number;
  monthName: string;
  amount: number;
}

export interface MetricResponse {
  year: number;
  currentValue: number;
  previousValue: number;
  changePercent: string;
  yearlyAverage: number;
  yearlyMax: number;
  monthlyData: MonthlyMetric[];
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

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

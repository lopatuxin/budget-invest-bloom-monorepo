import type { QueryClient } from '@tanstack/react-query';

export const qk = {
  // budget domain
  budget: {
    summary: (month: string, year: string) => ['budget-summary', month, year] as const,
    overview: (month: string, year: string) => ['overview-summary', month, year] as const,
    freeCapital: () => ['free-capital'] as const,
    categoryAnalytics: (name: string, year: number, month: number) =>
      ['category-analytics', name, year, month] as const,
    categoryAnalyticsAll: () => ['category-analytics'] as const,
  },
  metrics: {
    balance: (year: number) => ['balance-metric', year] as const,
    capital: (year: number) => ['capital-metric', year] as const,
    expense: (year: number) => ['expense-metric', year] as const,
    income: (year: number) => ['income-metric', year] as const,
    inflation: (year: number) => ['inflation-metric', year] as const,
  },
  // investment domain
  investment: {
    portfolio: () => ['investment-portfolio'] as const,
    transactions: (ticker?: string) =>
      (ticker ? ['investment-transactions', ticker] : ['investment-transactions']) as const,
    portfolioValueHistory: () => ['portfolio-value-history'] as const,
    positionByTicker: (ticker: string) => ['position-by-ticker', ticker] as const,
    securityList: () => ['moex-list'] as const,
    securitySearch: (q: string) => ['moex-search', q] as const,
    securityPriceHistory: (ticker: string) => ['security-price-history', ticker] as const,
    securityDividends: (ticker: string) => ['security-dividends-history', ticker] as const,
    securitySnapshot: () => ['security-snapshot'] as const,
    projection: (request: unknown) => ['projection', request] as const,
  },
};

// Invalidates all budget-related caches after mutations
export const invalidateBudgetCaches = (queryClient: QueryClient) => {
  queryClient.invalidateQueries({ queryKey: ['budget-summary'] });
  queryClient.invalidateQueries({ queryKey: ['overview-summary'] });
  queryClient.invalidateQueries({ queryKey: ['free-capital'] });
  queryClient.invalidateQueries({ queryKey: ['category-analytics'] });
  queryClient.invalidateQueries({ queryKey: ['balance-metric'] });
  queryClient.invalidateQueries({ queryKey: ['capital-metric'] });
  queryClient.invalidateQueries({ queryKey: ['expense-metric'] });
  queryClient.invalidateQueries({ queryKey: ['income-metric'] });
  queryClient.invalidateQueries({ queryKey: ['inflation-metric'] });
};

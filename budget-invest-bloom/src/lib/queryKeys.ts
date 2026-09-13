import type { QueryClient } from '@tanstack/react-query';
import type { PortfolioSort } from '@/types/investment';

export const qk = {
  // budget domain
  budget: {
    summary: (month: string, year: string) => ['budget-summary', month, year] as const,
    overviewPage: () => ['overview-page'] as const,
    analyticsPage: (year: number) => ['analytics-page', year] as const,
    categoryPage: (name: string, month: number, year: number) =>
      ['category-page', name, month, year] as const,
    operations: (month: string, year: string) => ['budget-operations', month, year] as const,
    categoryList: () => ['budget-category-list'] as const,
  },
  // investment domain
  investment: {
    portfolio: (sort: PortfolioSort) => ['investment-portfolio', sort] as const,
    // Prefix match invalidates the cached page for every sort order
    portfolioAll: () => ['investment-portfolio'] as const,
    transactions: (ticker?: string) =>
      ticker ? (['investment-transactions', ticker] as const) : (['investment-transactions'] as const),
    portfolioValueHistory: () => ['portfolio-value-history'] as const,
    securityList: () => ['moex-list'] as const,
    securitySearch: (q: string) => ['moex-search', q] as const,
    securityPriceHistory: (ticker: string) => ['security-price-history', ticker] as const,
    projection: (request: unknown) => ['projection', request] as const,
    securityPage: (ticker: string) => ['security-page', ticker] as const,
    // Prefix match invalidates the cached page for every ticker
    securityPageAll: () => ['security-page'] as const,
  },
};

// Invalidates all budget-related caches after mutations
export const invalidateBudgetCaches = (queryClient: QueryClient) => {
  queryClient.invalidateQueries({ queryKey: ['budget-summary'] });
  queryClient.invalidateQueries({ queryKey: ['budget-operations'] });
  queryClient.invalidateQueries({ queryKey: ['overview-page'] });
  queryClient.invalidateQueries({ queryKey: ['analytics-page'] });
  queryClient.invalidateQueries({ queryKey: ['category-page'] });
};

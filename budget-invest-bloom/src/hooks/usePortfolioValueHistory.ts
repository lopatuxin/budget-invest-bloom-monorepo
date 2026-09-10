import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { qk } from '@/lib/queryKeys';
import type { ApiResponse, PortfolioValueHistoryResponse } from '@/types/investment';

export function usePortfolioValueHistory(from: string, to: string) {
  return useQuery({
    queryKey: [...qk.investment.portfolioValueHistory(), from, to],
    queryFn: () =>
      apiPost<ApiResponse<PortfolioValueHistoryResponse>>('/api/investment/analytics/portfolio/value-history', { from, to }),
    staleTime: 5 * 60 * 1000,
    // Switching the period changes the key; without this the chart collapses to
    // its loading state on every click instead of redrawing in place.
    placeholderData: keepPreviousData,
  });
}

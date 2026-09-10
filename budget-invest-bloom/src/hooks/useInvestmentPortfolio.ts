import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { qk } from '@/lib/queryKeys';
import type { ApiResponse, PortfolioPageResponse, PortfolioSort } from '@/types/investment';

export function useInvestmentPortfolio(sort: PortfolioSort = 'WEIGHT', enabled = true) {
  return useQuery({
    queryKey: qk.investment.portfolio(sort),
    queryFn: () =>
      apiPost<ApiResponse<PortfolioPageResponse>>('/api/investment/portfolio/page', { sort }),
    enabled,
    staleTime: 30_000,
    // Sort change swaps the query key — keep the previous sort's data on screen
    // while the new one loads instead of dropping to the full-page skeleton.
    placeholderData: keepPreviousData,
  });
}

import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { qk } from '@/lib/queryKeys';
import type { ApiResponse, PortfolioPageResponse } from '@/types/investment';

export function useInvestmentPortfolio(enabled = true) {
  return useQuery({
    queryKey: qk.investment.portfolio(),
    queryFn: () =>
      apiPost<ApiResponse<PortfolioPageResponse>>('/api/investment/portfolio/page', {}),
    enabled,
    staleTime: 30_000,
  });
}

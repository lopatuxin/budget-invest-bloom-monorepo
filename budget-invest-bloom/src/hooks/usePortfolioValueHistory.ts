import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import type { ApiResponse, PortfolioValuePoint, SeriesResponse } from '@/types/investment';

export function usePortfolioValueHistory(from: string, to: string) {
  return useQuery({
    queryKey: ['portfolio-value-history', from, to],
    queryFn: () =>
      apiPost<ApiResponse<SeriesResponse<PortfolioValuePoint>>>('/api/investment/analytics/portfolio/value-history', { from, to }),
    staleTime: 5 * 60 * 1000,
  });
}

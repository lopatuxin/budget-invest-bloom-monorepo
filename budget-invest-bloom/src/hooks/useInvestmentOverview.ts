import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import type { ApiResponse, PortfolioOverview } from '@/types/investment';

export function useInvestmentOverview() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['investment-overview'],
    queryFn: () =>
      apiPost<ApiResponse<PortfolioOverview>>('/api/investment/portfolio/overview', {}),
  });

  return { data, isLoading, error };
}

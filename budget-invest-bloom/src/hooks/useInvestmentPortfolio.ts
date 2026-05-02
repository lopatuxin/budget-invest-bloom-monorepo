import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import type { ApiResponse, PortfolioPageResponse } from '@/types/investment';

export function useInvestmentPortfolio() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['investment-portfolio'],
    queryFn: () =>
      apiPost<ApiResponse<PortfolioPageResponse>>('/api/investment/portfolio/page', {}),
  });

  return { data, isLoading, error };
}

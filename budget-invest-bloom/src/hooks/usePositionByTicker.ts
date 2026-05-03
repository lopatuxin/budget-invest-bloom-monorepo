import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import type { ApiResponse, PositionResponse } from '@/types/investment';

export function usePositionByTicker(ticker: string | null) {
  return useQuery({
    queryKey: ['position-by-ticker', ticker],
    queryFn: () =>
      apiPost<ApiResponse<PositionResponse>>('/api/investment/portfolio/positions/by-ticker', { ticker }),
    enabled: !!ticker,
  });
}

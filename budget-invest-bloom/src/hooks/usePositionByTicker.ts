import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { qk } from '@/lib/queryKeys';
import type { ApiResponse, PositionResponse } from '@/types/investment';

export function usePositionByTicker(ticker: string | null) {
  return useQuery({
    queryKey: ticker ? qk.investment.positionByTicker(ticker) : ['position-by-ticker', null],
    queryFn: () =>
      apiPost<ApiResponse<PositionResponse>>('/api/investment/portfolio/positions/by-ticker', { ticker }),
    enabled: !!ticker,
  });
}

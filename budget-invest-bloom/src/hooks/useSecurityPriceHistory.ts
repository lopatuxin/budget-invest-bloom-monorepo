import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { qk } from '@/lib/queryKeys';
import type { ApiResponse, PricePoint, SeriesResponse } from '@/types/investment';

export function useSecurityPriceHistory(ticker: string | null, from: string, to: string) {
  return useQuery({
    queryKey: ticker
      ? [...qk.investment.securityPriceHistory(ticker), from, to]
      : ['security-price-history', null, from, to],
    queryFn: () =>
      apiPost<ApiResponse<SeriesResponse<PricePoint>>>('/api/investment/analytics/security/price-history', { ticker, from, to }),
    enabled: !!ticker,
    staleTime: 5 * 60 * 1000,
  });
}

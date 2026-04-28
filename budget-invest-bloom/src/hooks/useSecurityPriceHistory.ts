import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import type { ApiResponse, PricePoint } from '@/types/investment';

export function useSecurityPriceHistory(ticker: string | null, from: string, to: string) {
  return useQuery({
    queryKey: ['security-price-history', ticker, from, to],
    queryFn: () =>
      apiPost<ApiResponse<PricePoint[]>>('/api/investment/analytics/security/price-history', { ticker, from, to }),
    enabled: !!ticker,
    staleTime: 5 * 60 * 1000,
  });
}

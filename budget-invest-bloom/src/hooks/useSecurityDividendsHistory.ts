import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import type { ApiResponse, PaidDividend } from '@/types/investment';

export function useSecurityDividendsHistory(ticker: string | null) {
  return useQuery({
    queryKey: ['security-dividends-history', ticker],
    queryFn: () =>
      apiPost<ApiResponse<PaidDividend[]>>('/api/investment/analytics/security/dividends-history', { ticker }),
    enabled: !!ticker,
    staleTime: 5 * 60 * 1000,
  });
}

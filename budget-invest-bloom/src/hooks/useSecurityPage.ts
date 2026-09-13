import { useQuery } from '@tanstack/react-query';
import { apiPost, ApiError } from '@/lib/api';
import { qk } from '@/lib/queryKeys';
import type { ApiResponse, SecurityPageResponse } from '@/types/investment';

export function useSecurityPage(ticker: string | null) {
  return useQuery({
    queryKey: ticker ? qk.investment.securityPage(ticker) : ['security-page', null],
    queryFn: () => apiPost<ApiResponse<SecurityPageResponse>>('/api/investment/securities/page', { ticker }),
    enabled: !!ticker,
    staleTime: 30_000,
    retry: (failureCount, error) => {
      // A 404 means the ticker has no transactions in the portfolio — retrying would just fail
      // again, and the retry pause (isLoading false, error still null) briefly renders the wrong
      // "failed to load" state instead of the "not in portfolio" empty state. Everything else
      // keeps the project default (1 retry).
      if (error instanceof ApiError && error.status === 404) return false;
      return failureCount < 1;
    },
  });
}

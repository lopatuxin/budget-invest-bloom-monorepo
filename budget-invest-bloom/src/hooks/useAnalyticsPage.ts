import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { qk } from '@/lib/queryKeys';
import type { AnalyticsPageResponse, ApiResponse } from '@/types/budget';

export function useAnalyticsPage(year: number) {
  return useQuery({
    queryKey: qk.budget.analyticsPage(year),
    queryFn: () => apiPost<ApiResponse<AnalyticsPageResponse>>('/api/budget/analytics', { year }),
    staleTime: 30_000,
    placeholderData: keepPreviousData,
  });
}

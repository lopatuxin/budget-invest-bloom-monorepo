import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { qk } from '@/lib/queryKeys';
import type { ApiResponse, OverviewSummaryResponse } from '@/types/budget';

export function useOverviewSummary(month: string, year: string, enabled = true) {
  return useQuery({
    queryKey: qk.budget.overview(month, year),
    queryFn: () =>
      apiPost<ApiResponse<OverviewSummaryResponse>>('/api/budget/overview', {
        month: Number(month),
        year: Number(year),
      }),
    enabled,
    staleTime: 30_000,
  });
}

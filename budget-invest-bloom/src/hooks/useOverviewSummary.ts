import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import type { ApiResponse, OverviewSummaryResponse } from '@/types/budget';

export function useOverviewSummary(month: string, year: string, enabled = true) {
  const { data, isLoading, error } = useQuery({
    queryKey: ['overview-summary', month, year],
    queryFn: () =>
      apiPost<ApiResponse<OverviewSummaryResponse>>('/api/budget/overview', {
        month: Number(month),
        year: Number(year),
      }),
    enabled,
  });

  return { data, isLoading, error };
}

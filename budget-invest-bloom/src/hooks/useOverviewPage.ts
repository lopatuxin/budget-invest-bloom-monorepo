import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { qk } from '@/lib/queryKeys';
import type { ApiResponse, OverviewPageResponse } from '@/types/budget';

export function useOverviewPage(enabled = true) {
  return useQuery({
    queryKey: qk.budget.overviewPage(),
    queryFn: () => apiPost<ApiResponse<OverviewPageResponse>>('/api/budget/overview', {}),
    enabled,
    staleTime: 30_000,
  });
}

import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { qk } from '@/lib/queryKeys';
import type { ApiResponse, LifetimeBalanceResponse } from '@/types/budget';

export function useFreeCapital(enabled = true) {
  return useQuery({
    queryKey: qk.budget.freeCapital(),
    // Convention: all reads go through POST + ApiRequest so gateway validates JWT once.
    queryFn: () =>
      apiPost<ApiResponse<LifetimeBalanceResponse>>('/api/budget/balance/lifetime', {}),
    enabled,
    staleTime: 30_000,
  });
}

import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import type { ApiResponse, LifetimeBalanceResponse } from '@/types/budget';

export function useFreeCapital(enabled = true) {
  const { data, isLoading, error } = useQuery({
    queryKey: ['free-capital'],
    queryFn: () =>
      apiPost<ApiResponse<LifetimeBalanceResponse>>('/api/budget/balance/lifetime', {}),
    enabled,
  });
  return { data, isLoading, error };
}

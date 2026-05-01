import { useQuery } from '@tanstack/react-query';
import { apiGet } from '@/lib/api';
import type { ApiResponse, MoexSecuritySearchItem } from '@/types/investment';
import type { SearchCategory } from '@/hooks/useSecuritySearch';

export function useSecurityList(category: SearchCategory, enabled: boolean) {
  return useQuery({
    queryKey: ['moex-list', category],
    queryFn: () =>
      apiGet<ApiResponse<MoexSecuritySearchItem[]>>(
        `/api/investment/market/securities?category=${category}`,
      ),
    enabled,
    staleTime: 5 * 60_000, // 5 min — board listings rarely change
  });
}

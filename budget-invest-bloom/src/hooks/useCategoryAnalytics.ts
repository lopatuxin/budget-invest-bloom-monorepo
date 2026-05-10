import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { qk } from '@/lib/queryKeys';
import type { ApiResponse, CategoryAnalyticsResponse } from '@/types/budget';

export function useCategoryAnalytics(categoryName: string, year: number, month: number) {
  return useQuery({
    queryKey: qk.budget.categoryAnalytics(categoryName, year, month),
    queryFn: () =>
      apiPost<ApiResponse<CategoryAnalyticsResponse>>('/api/budget/categories/analytics', {
        categoryName,
        year,
        month,
      }),
    enabled: !!categoryName && year > 0 && month >= 1 && month <= 12,
    staleTime: 30_000,
  });
}

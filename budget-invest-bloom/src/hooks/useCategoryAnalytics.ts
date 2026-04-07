import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import type { ApiResponse, CategoryAnalyticsResponse } from '@/types/budget';

export function useCategoryAnalytics(categoryName: string, year: number, month: number) {
  const { data, isLoading, error } = useQuery({
    queryKey: ['categoryAnalytics', categoryName, year, month],
    queryFn: () =>
      apiPost<ApiResponse<CategoryAnalyticsResponse>>('/api/budget/categories/analytics', {
        categoryName,
        year,
        month,
      }),
    enabled: !!categoryName,
  });

  return { data, isLoading, error };
}

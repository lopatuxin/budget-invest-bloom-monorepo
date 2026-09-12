import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { apiPost, ApiError } from '@/lib/api';
import { qk } from '@/lib/queryKeys';
import type { ApiResponse, CategoryPageResponse } from '@/types/budget';

export function useCategoryPage(categoryName: string, month: number, year: number) {
  return useQuery({
    queryKey: qk.budget.categoryPage(categoryName, month, year),
    queryFn: () =>
      apiPost<ApiResponse<CategoryPageResponse>>('/api/budget/categories/page', {
        categoryName,
        month,
        year,
      }),
    enabled: !!categoryName,
    staleTime: 30_000,
    retry: (failureCount, error) => {
      // A 404 means the category name in the address is wrong or was renamed — retrying
      // the same request would just fail again. Everything else keeps the project default (1 retry).
      if (error instanceof ApiError && error.status === 404) return false;
      return failureCount < 1;
    },
    placeholderData: keepPreviousData,
  });
}

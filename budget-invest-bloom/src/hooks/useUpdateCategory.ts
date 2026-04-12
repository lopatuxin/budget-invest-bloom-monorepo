import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import type { ApiResponse } from '@/types/budget';

interface UpdateCategoryParams {
  categoryId: string;
  name: string;
  budget: number;
  emoji?: string;
}

export function useUpdateCategory() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (params: UpdateCategoryParams) =>
      apiPost<ApiResponse<unknown>>('/api/budget/categories/update', params),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categoryAnalytics'] });
      queryClient.invalidateQueries({ queryKey: ['budget-summary'] });
    },
  });
}

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import type { ApiResponse } from '@/types/budget';

interface DeleteCategoryParams {
  categoryId: string;
  force?: boolean;
}

export function useDeleteCategory() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (params: DeleteCategoryParams) =>
      apiPost<ApiResponse<unknown>>('/api/budget/categories/delete', params),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categoryAnalytics'] });
      queryClient.invalidateQueries({ queryKey: ['budget-summary'] });
    },
  });
}

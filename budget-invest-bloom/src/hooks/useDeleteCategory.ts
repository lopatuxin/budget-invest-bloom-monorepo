import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiPost, ApiError } from '@/lib/api';
import { useToast } from '@/hooks/use-toast';
import { invalidateBudgetCaches } from '@/lib/queryKeys';
import type { ApiResponse } from '@/types/budget';

interface DeleteCategoryParams {
  categoryId: string;
  force?: boolean;
}

/** The 409 that starts the two-step delete confirmation (see CategoryDeleteFlow) — not a failure. */
export function isCategoryHasExpensesError(error: unknown): error is ApiError {
  return error instanceof ApiError && error.status === 409 && error.code === 'CATEGORY_HAS_EXPENSES';
}

export function useDeleteCategory() {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  return useMutation({
    mutationFn: (params: DeleteCategoryParams) =>
      apiPost<ApiResponse<unknown>>('/api/budget/categories/delete', params),
    onSuccess: () => {
      invalidateBudgetCaches(queryClient);
      toast({ title: 'Категория удалена' });
    },
    onError: (error: unknown) => {
      // Not a failure — it's the first step of a two-step confirmation (see CategoryDeleteFlow),
      // which opens its own dialog for it.
      if (isCategoryHasExpensesError(error)) {
        return;
      }
      const message = error instanceof Error ? error.message : 'Не удалось удалить категорию';
      toast({ variant: 'destructive', title: 'Ошибка', description: message });
    },
  });
}

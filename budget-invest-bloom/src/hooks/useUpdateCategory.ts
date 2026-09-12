import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiPost, isCategoryNameTakenError } from '@/lib/api';
import { useToast } from '@/hooks/use-toast';
import { invalidateBudgetCaches } from '@/lib/queryKeys';
import type { ApiResponse } from '@/types/budget';

interface UpdateCategoryParams {
  categoryId: string;
  name: string;
  // Category limits are out of scope for every UI that calls this hook — omit
  // the field entirely rather than sending a value nobody edits.
  budget?: number;
  emoji?: string;
}


export function useUpdateCategory() {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  return useMutation({
    mutationFn: (params: UpdateCategoryParams) =>
      apiPost<ApiResponse<unknown>>('/api/budget/categories/update', params),
    onSuccess: () => {
      invalidateBudgetCaches(queryClient);
      toast({ title: 'Категория обновлена' });
    },
    onError: (error: unknown) => {
      // Not a failure — it's ordinary validation that CategoryFormDialog shows under the name field.
      if (isCategoryNameTakenError(error)) {
        return;
      }
      const message = error instanceof Error ? error.message : 'Не удалось обновить категорию';
      toast({ variant: 'destructive', title: 'Ошибка', description: message });
    },
  });
}

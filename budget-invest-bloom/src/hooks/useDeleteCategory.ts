import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { useToast } from '@/hooks/use-toast';
import { invalidateBudgetCaches } from '@/lib/queryKeys';
import type { ApiResponse } from '@/types/budget';

interface DeleteCategoryParams {
  categoryId: string;
  force?: boolean;
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
      const message = error instanceof Error ? error.message : 'Не удалось удалить категорию';
      toast({ variant: 'destructive', title: 'Ошибка', description: message });
    },
  });
}

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { useToast } from '@/hooks/use-toast';
import { invalidateBudgetCaches, qk } from '@/lib/queryKeys';
import type { ApiResponse } from '@/types/budget';

interface CreateCategoryParams {
  name: string;
  budget: number | null;
  emoji?: string;
}

export function useCreateCategory() {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  return useMutation({
    mutationFn: (data: CreateCategoryParams) =>
      apiPost<ApiResponse<unknown>>('/api/budget/categories', data),
    onSuccess: (_data, variables) => {
      invalidateBudgetCaches(queryClient);
      queryClient.invalidateQueries({ queryKey: qk.budget.categoryList() });
      toast({ title: 'Категория создана', description: `Добавлена категория "${variables.name}"` });
    },
    onError: (error: unknown) => {
      const message = error instanceof Error ? error.message : 'Не удалось добавить категорию';
      toast({ title: 'Ошибка', description: message, variant: 'destructive' });
    },
  });
}

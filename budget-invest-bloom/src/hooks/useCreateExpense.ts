import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { useToast } from '@/hooks/use-toast';
import { invalidateBudgetCaches } from '@/lib/queryKeys';
import type { ApiResponse } from '@/types/budget';

interface CreateExpenseParams {
  categoryId: string;
  amount: number;
  description: string | null;
  date: string;
}

export function useCreateExpense() {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  return useMutation({
    mutationFn: (data: CreateExpenseParams) =>
      apiPost<ApiResponse<unknown>>('/api/budget/expenses', data),
    onSuccess: () => {
      invalidateBudgetCaches(queryClient);
      toast({ title: 'Расход записан' });
    },
    onError: (error: unknown) => {
      const message = error instanceof Error ? error.message : 'Не удалось добавить расход';
      toast({ title: 'Ошибка', description: message, variant: 'destructive' });
    },
  });
}

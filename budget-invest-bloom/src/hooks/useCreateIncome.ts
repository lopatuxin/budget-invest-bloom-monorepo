import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { useToast } from '@/hooks/use-toast';
import { invalidateBudgetCaches } from '@/lib/queryKeys';
import type { ApiResponse } from '@/types/budget';

interface CreateIncomeParams {
  amount: number;
  source: string;
  description: string | null;
  date: string;
}

export function useCreateIncome() {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  return useMutation({
    mutationFn: (data: CreateIncomeParams) =>
      apiPost<ApiResponse<unknown>>('/api/budget/incomes', data),
    onSuccess: () => {
      invalidateBudgetCaches(queryClient);
      toast({ title: 'Доход записан' });
    },
    onError: (error: unknown) => {
      const message = error instanceof Error ? error.message : 'Не удалось добавить доход';
      toast({ title: 'Ошибка', description: message, variant: 'destructive' });
    },
  });
}

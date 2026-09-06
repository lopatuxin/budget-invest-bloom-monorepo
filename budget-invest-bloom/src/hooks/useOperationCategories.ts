import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { qk } from '@/lib/queryKeys';
import type { ApiResponse, OperationCategory } from '@/types/budget';

// Categories for the global "new operation" form — sorted by the backend by
// recent usage frequency. Deliberately its own endpoint/hook, not a reuse of
// useBudgetSummary: the form opens from any page, not only /budget.
export function useOperationCategories(enabled = true) {
  return useQuery({
    queryKey: qk.budget.categoryList(),
    queryFn: () => apiPost<ApiResponse<OperationCategory[]>>('/api/budget/categories/list', {}),
    enabled,
    staleTime: 30_000,
  });
}

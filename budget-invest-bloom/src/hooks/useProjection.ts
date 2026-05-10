import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { qk } from '@/lib/queryKeys';
import type { ApiResponse, ProjectionRequest, ProjectionResult } from '@/types/investment';

export function useProjection(request: ProjectionRequest | null) {
  return useQuery({
    queryKey: qk.investment.projection(request),
    queryFn: () =>
      apiPost<ApiResponse<ProjectionResult>>('/api/investment/analytics/projection', request!),
    enabled: false,
    staleTime: 0,
  });
}

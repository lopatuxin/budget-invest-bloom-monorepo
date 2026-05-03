import { useMutation } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import type { ApiResponse, ProjectionRequest, ProjectionResult } from '@/types/investment';

export function useProjection() {
  return useMutation({
    mutationFn: (request: ProjectionRequest) =>
      apiPost<ApiResponse<ProjectionResult>>('/api/investment/analytics/projection', request),
  });
}

import { useMutation } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import type { ProjectionRequest, ProjectionResult } from '@/types/investment';

export function useProjection() {
  return useMutation({
    mutationFn: (request: ProjectionRequest) =>
      apiPost<ProjectionResult>('/investment/analytics/projection', request),
  });
}

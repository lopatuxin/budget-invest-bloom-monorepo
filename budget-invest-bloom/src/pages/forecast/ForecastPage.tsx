import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, TrendingUp } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { RetryErrorCard } from '@/components/RetryErrorCard';
import EmptyState from '@/components/EmptyState';
import { Skeleton } from '@/components/ui/skeleton';
import { TransactionDialog } from '@/components/investments/TransactionDialog';
import { useProjection } from '@/hooks/useProjection';
import { ForecastForm } from '@/pages/forecast/ForecastForm';
import { ForecastTiles } from '@/pages/forecast/ForecastTiles';
import { ForecastChart } from '@/pages/forecast/ForecastChart';
import { ForecastBreakdownCard } from '@/pages/forecast/ForecastBreakdownCard';
import type { ProjectionRequest } from '@/types/investment';

export function ForecastPage() {
  const [request, setRequest] = useState<ProjectionRequest | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const { data, isFetching, error, refetch } = useProjection(request);
  const result = data?.body;

  const horizonYears = request ? Math.round(request.horizonMonths / 12) : 0;

  return (
    <div className="flex flex-col gap-4 lg:gap-5 pb-6">
      <TransactionDialog open={dialogOpen} onOpenChange={setDialogOpen} />

      <div className="flex items-center justify-between h-11">
        <div className="flex items-baseline gap-3.5">
          <h1 className="font-display text-[26px] lg:text-[34px] leading-none text-app-text">Прогноз</h1>
          <span className="hidden lg:inline text-app-text-dim text-[13px]">если портфель растёт как в среднем росли его бумаги</span>
        </div>
        <Button asChild variant="outline" size="sm" className="gap-1.5 border-app-border-strong bg-app-surface text-app-text hover:bg-app-surface-2">
          <Link to="/investments">
            <ArrowLeft aria-hidden="true" className="w-3.5 h-3.5" />
            Инвестиции
          </Link>
        </Button>
      </div>

      <ForecastForm onSubmit={setRequest} isPending={isFetching} />

      {error ? (
        <RetryErrorCard message="Не удалось рассчитать прогноз" onRetry={refetch} />
      ) : isFetching ? (
        <div className="flex flex-col gap-4 lg:gap-5">
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            {Array.from({ length: 4 }).map((_, index) => (
              <Skeleton key={index} className="h-24 bg-app-border" />
            ))}
          </div>
          <Skeleton className="h-72 bg-app-border" />
        </div>
      ) : !result ? (
        <p className="text-app-text-dim text-sm">задайте параметры и нажмите «Рассчитать»</p>
      ) : result.startValue === 0 ? (
        <div className="glass-card p-8">
          <EmptyState
            icon={<TrendingUp className="w-12 h-12" />}
            title="Портфель пуст"
            description="Запишите первую сделку, чтобы построить прогноз"
            actionLabel="Записать сделку"
            onAction={() => setDialogOpen(true)}
          />
        </div>
      ) : (
        <>
          <ForecastTiles result={result} horizonYears={horizonYears} />
          <ForecastChart series={result.series} pendingHistoryTickers={result.pendingHistoryTickers} />
          <ForecastBreakdownCard result={result} />
        </>
      )}
    </div>
  );
}

import { useEffect } from 'react';
import { Skeleton } from '@/components/ui/skeleton';
import { RetryErrorCard } from '@/components/RetryErrorCard';
import { useToast } from '@/hooks/use-toast';
import { useOperationDialog } from '@/components/operation/OperationDialogProvider';
import { useOverviewPage } from '@/hooks/useOverviewPage';
import { OverviewCapitalCard } from '@/pages/overview/OverviewCapitalCard';
import { OverviewTiles } from '@/pages/overview/OverviewTiles';
import { OverviewIncomeChart } from '@/pages/overview/OverviewIncomeChart';
import { OverviewYearTotals } from '@/pages/overview/OverviewYearTotals';
import { formatFullDate, monthNominative } from '@/pages/overview/overviewFormat';

function OverviewLoadingSkeleton() {
  return (
    <div className="flex flex-col gap-4 lg:gap-5">
      <Skeleton className="h-[250px] bg-app-border" />
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-2.5 lg:gap-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="h-[92px] bg-app-border" />
        ))}
      </div>
      <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_360px] gap-4 lg:gap-5">
        <Skeleton className="h-[300px] bg-app-border" />
        <Skeleton className="h-[300px] bg-app-border" />
      </div>
    </div>
  );
}

export function OverviewPage() {
  const { toast } = useToast();
  const { openOperationDialog } = useOperationDialog();
  const { data, isLoading, error, refetch } = useOverviewPage();
  const overview = data?.body;

  useEffect(() => {
    if (error) {
      toast({
        title: 'Ошибка загрузки',
        description: error instanceof Error ? error.message : 'Не удалось загрузить обзор',
        variant: 'destructive',
      });
    }
  }, [error, toast]);

  const hasNoRecords = Boolean(
    overview && overview.capital.total === 0 && overview.capital.freeMoney === 0 && overview.portfolio.assetsCount === 0
  );

  return (
    <div className="flex flex-col gap-4 lg:gap-5 pb-6">
      <div className="flex items-baseline justify-between h-11">
        <h1 className="font-display text-[26px] lg:text-[34px] leading-none text-app-text">Обзор</h1>
        {overview && <span className="text-app-text-dim text-xs lg:text-[13px]">{formatFullDate(overview.asOf)}</span>}
      </div>

      {isLoading ? (
        <OverviewLoadingSkeleton />
      ) : error || !overview ? (
        <RetryErrorCard message="Не удалось загрузить обзор" onRetry={refetch} />
      ) : (
        <>
          <OverviewCapitalCard
            capital={overview.capital}
            portfolioAvailable={overview.portfolio.available}
            hasNoRecords={hasNoRecords}
            onRecordExpense={() => openOperationDialog('expense')}
          />
          <OverviewTiles data={overview} hasNoRecords={hasNoRecords} />
          <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_360px] gap-4 lg:gap-5 lg:items-stretch">
            <div className="order-2 lg:order-1">
              <OverviewIncomeChart months={overview.months} />
            </div>
            <div className="order-1 lg:order-2">
              <OverviewYearTotals
                totals12m={overview.totals12m}
                personalInflationPercent={overview.personalInflationPercent}
                isCurrentMonthPartial={overview.currentMonth.partial}
                currentMonthLabel={monthNominative(overview.currentMonth.month)}
              />
            </div>
          </div>
        </>
      )}
    </div>
  );
}

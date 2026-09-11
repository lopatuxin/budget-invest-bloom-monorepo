import { useEffect } from 'react';
import { Navigate, useParams, useSearchParams } from 'react-router-dom';
import { BarChart3 } from 'lucide-react';
import { Skeleton } from '@/components/ui/skeleton';
import { RetryErrorCard } from '@/components/RetryErrorCard';
import EmptyState from '@/components/EmptyState';
import { StatTile } from '@/components/StatTile';
import { useToast } from '@/hooks/use-toast';
import { useOperationDialog } from '@/components/operation/OperationDialogProvider';
import { useAnalyticsPage } from '@/hooks/useAnalyticsPage';
import { AnalyticsHeader } from '@/pages/analytics/AnalyticsHeader';
import { AnalyticsTiles } from '@/pages/analytics/AnalyticsTiles';
import { AnalyticsYearChart } from '@/pages/analytics/AnalyticsYearChart';
import { AnalyticsCategoryTable } from '@/pages/analytics/AnalyticsCategoryTable';
import { AnalyticsCategoryList } from '@/pages/analytics/AnalyticsCategoryList';
import { ANALYTICS_EXTREMES_LABEL, ANALYTICS_TOTAL_LABEL, parseAnalyticsTab, type AnalyticsTab } from '@/pages/analytics/analyticsFormat';

function AnalyticsLoadingSkeleton({ showCategoryCard }: { showCategoryCard: boolean }) {
  return (
    <div className="flex flex-col gap-4 lg:gap-5">
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-2.5 lg:gap-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="h-[92px] bg-app-border" />
        ))}
      </div>
      <Skeleton className="h-[300px] bg-app-border" />
      {showCategoryCard && <Skeleton className="h-[420px] bg-app-border" />}
    </div>
  );
}

const FOURTH_TILE_LABEL: Record<AnalyticsTab, (year: number) => string> = {
  expenses: () => 'Личная инфляция',
  income: (year) => `Сбережено за ${year}`,
  savings: () => 'Норма сбережений',
};

function AnalyticsEmptyTiles({ tab, year }: { tab: AnalyticsTab; year: number }) {
  const subtitle = `записей за ${year} нет`;
  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-2.5 lg:gap-4">
      <StatTile label={ANALYTICS_TOTAL_LABEL[tab](year)} value="—" subtitle={subtitle} />
      <StatTile label="В среднем за месяц" value="—" subtitle={subtitle} />
      <StatTile label={ANALYTICS_EXTREMES_LABEL[tab]} value="—" subtitle={subtitle} />
      <StatTile label={FOURTH_TILE_LABEL[tab](year)} value="—" subtitle={subtitle} />
    </div>
  );
}

export function AnalyticsPage() {
  const { tab: tabParam } = useParams<{ tab?: string }>();
  const [searchParams] = useSearchParams();
  const { toast } = useToast();
  const { openOperationDialog } = useOperationDialog();

  const tab = parseAnalyticsTab(tabParam);
  const yearParam = Number(searchParams.get('year'));
  const year = Number.isInteger(yearParam) && yearParam > 0 ? yearParam : new Date().getFullYear();

  const { data, isLoading, error, refetch } = useAnalyticsPage(year);
  const analytics = data?.body;

  useEffect(() => {
    if (error) {
      toast({
        title: 'Ошибка загрузки',
        description: error instanceof Error ? error.message : 'Не удалось загрузить аналитику',
        variant: 'destructive',
      });
    }
  }, [error, toast]);

  if (tab === null) {
    return <Navigate to="/analytics" replace />;
  }

  const hasNoRecords = Boolean(analytics && analytics.expenses.total === 0 && analytics.income.total === 0);
  const showCategoryCard = tab === 'expenses' && !hasNoRecords && Boolean(analytics?.categories.length);

  return (
    <div className="flex flex-col gap-4 lg:gap-5 pb-6">
      <AnalyticsHeader tab={tab} year={year} previousYear={year - 1} earliestYear={analytics?.earliestYear} />

      {isLoading && !analytics ? (
        <AnalyticsLoadingSkeleton showCategoryCard={tab === 'expenses'} />
      ) : error || !analytics ? (
        <RetryErrorCard message="Не удалось загрузить аналитику" onRetry={refetch} />
      ) : hasNoRecords ? (
        <>
          <AnalyticsEmptyTiles tab={tab} year={year} />
          <div className="h-[300px]">
            <EmptyState
              icon={<BarChart3 className="w-10 h-10" />}
              title={`За ${year} записей нет`}
              description="Записи появятся здесь по мере ведения бюджета"
              actionLabel={year === new Date().getFullYear() ? 'Записать расход' : undefined}
              onAction={year === new Date().getFullYear() ? () => openOperationDialog('expense') : undefined}
            />
          </div>
        </>
      ) : (
        <>
          <AnalyticsTiles tab={tab} data={analytics} />
          <AnalyticsYearChart
            tab={tab}
            year={analytics.year}
            previousYear={analytics.previousYear}
            previousYearHasData={analytics.previousYearHasData}
            months={(tab === 'expenses' ? analytics.expenses : tab === 'income' ? analytics.income : analytics.savings).months}
          />
          {showCategoryCard && (
            <>
              <div className="hidden lg:block">
                <AnalyticsCategoryTable
                  categories={analytics.categories}
                  expenses={analytics.expenses}
                  year={analytics.year}
                  previousYear={analytics.previousYear}
                  previousYearHasData={analytics.previousYearHasData}
                  personalInflationPercent={analytics.personalInflationPercent ?? null}
                />
              </div>
              <div className="lg:hidden">
                <AnalyticsCategoryList
                  categories={analytics.categories}
                  expenses={analytics.expenses}
                  previousYear={analytics.previousYear}
                  previousYearHasData={analytics.previousYearHasData}
                />
              </div>
            </>
          )}
        </>
      )}
    </div>
  );
}

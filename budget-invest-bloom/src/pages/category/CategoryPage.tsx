import { useEffect, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { FolderX } from 'lucide-react';
import { Skeleton } from '@/components/ui/skeleton';
import { RetryErrorCard } from '@/components/RetryErrorCard';
import EmptyState from '@/components/EmptyState';
import { CategoryFormDialog } from '@/components/CategoryFormDialog';
import { OperationsFeed } from '@/components/operation/OperationsFeed';
import { useOperationDialog } from '@/components/operation/OperationDialogProvider';
import { useToast } from '@/hooks/use-toast';
import { useCategoryPage } from '@/hooks/useCategoryPage';
import { ApiError } from '@/lib/api';
import { getBudgetPeriodKind, periodSubtitle } from '@/pages/budget/budgetPeriod';
import { CategoryHeader } from '@/pages/category/CategoryHeader';
import { CategorySummaryStrip } from '@/pages/category/CategorySummaryStrip';
import { CategoryMonthsChart } from '@/pages/category/CategoryMonthsChart';
import { CategoryDeleteFlow } from '@/pages/category/CategoryDeleteFlow';
import { backToBudgetHref } from '@/pages/category/categoryFormat';
import { monthNominative, monthPrepositional } from '@/lib/monthNames';

export function CategoryPage() {
  const { category: categoryFromUrl } = useParams<{ category: string }>();
  const categoryName = categoryFromUrl ?? '';
  const navigate = useNavigate();
  const { toast } = useToast();
  const { openOperationDialog } = useOperationDialog();
  const [searchParams] = useSearchParams();

  const now = new Date();
  // An out-of-range month/year in the address (typo, stale link) falls back to the current
  // month rather than being sent to the backend, which would 400 and leave the page empty.
  const monthParam = Number(searchParams.get('month'));
  const month = Number.isInteger(monthParam) && monthParam >= 1 && monthParam <= 12 ? monthParam : now.getMonth() + 1;
  const yearParam = Number(searchParams.get('year'));
  const year = Number.isInteger(yearParam) && yearParam >= 1950 && yearParam <= 2100 ? yearParam : now.getFullYear();
  const periodKind = getBudgetPeriodKind(month, year);
  const backHref = backToBudgetHref(month, year);

  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);

  const { data: response, isLoading, isFetching, isPlaceholderData, error, refetch } = useCategoryPage(categoryName, month, year);
  const data = response?.body;
  const is404 = error instanceof ApiError && error.status === 404;
  // isFetching alone also fires on a same-month background refetch (e.g. deleting an operation
  // invalidates the query) — isPlaceholderData narrows it to an actual month switch, while the
  // new month's data hasn't arrived yet and `data` still holds the previous month's.
  const isMonthLoading = isFetching && isPlaceholderData;

  useEffect(() => {
    if (error && !is404) {
      toast({
        title: 'Ошибка загрузки',
        description: error instanceof Error ? error.message : 'Не удалось загрузить категорию',
        variant: 'destructive',
      });
    }
  }, [error, is404, toast]);

  const goToMonth = (nextMonth: number, nextYear: number) => {
    navigate(`/budget/category/${encodeURIComponent(categoryName)}?month=${nextMonth}&year=${nextYear}`);
  };
  const handlePrevMonth = () => (month === 1 ? goToMonth(12, year - 1) : goToMonth(month - 1, year));
  const handleNextMonth = () => (month === 12 ? goToMonth(1, year + 1) : goToMonth(month + 1, year));

  const monthSubtitle = periodSubtitle(periodKind, data?.dayOfMonth, data?.daysInMonth);

  const handleRenamed = (newName: string) => {
    navigate(`/budget/category/${encodeURIComponent(newName)}?month=${month}&year=${year}`, { replace: true });
  };
  const handleDeleted = () => navigate(backHref);

  return (
    <div className="flex flex-col gap-5 pb-6">
      <CategoryHeader
        categoryName={data?.category.name ?? categoryName}
        emoji={data?.category.emoji}
        isSystem={data?.category.system ?? false}
        backHref={backHref}
        month={month}
        year={year}
        monthSubtitle={monthSubtitle}
        isMonthLoading={isMonthLoading}
        onPrevMonth={handlePrevMonth}
        onNextMonth={handleNextMonth}
        onEditRequest={() => data && setEditDialogOpen(true)}
        onDeleteRequest={() => data && setDeleteDialogOpen(true)}
      />

      {isLoading ? (
        <>
          <Skeleton className="h-[150px] bg-app-border" />
          <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_420px] gap-5 items-start">
            <Skeleton className="h-[330px] bg-app-border" />
            <Skeleton className="h-[400px] bg-app-border" />
          </div>
        </>
      ) : is404 ? (
        <EmptyState
          icon={<FolderX className="w-10 h-10" />}
          title={`Категории «${categoryName}» нет`}
          description="Возможно, её переименовали или удалили"
          actionLabel="К бюджету"
          onAction={() => navigate(backHref)}
        />
      ) : error ? (
        <RetryErrorCard message="Не удалось загрузить категорию" onRetry={refetch} />
      ) : data ? (
        <>
          <CategorySummaryStrip data={data} />
          <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_420px] gap-5 items-start">
            <CategoryMonthsChart months={data.months} norm={data.norm} normMonthsCounted={data.normMonthsCounted} />
            <OperationsFeed
              operations={data.operations}
              total={data.operationsCount}
              onEmptyAction={() => openOperationDialog('expense')}
              // Month taken from the response, not the address: on a month switch keepPreviousData
              // keeps the previous month's rows on screen, and a title built from the new address
              // would label them with a month they don't belong to.
              title={`Операции за ${monthNominative(data.period.month)}`}
              periodLabel=""
              showCategory={false}
              emptyTitle={`В ${monthPrepositional(data.period.month)} операций в этой категории нет`}
              emptyDescription="Новые расходы этой категории появятся здесь"
            />
          </div>
        </>
      ) : null}

      {data && (
        <>
          <CategoryFormDialog
            open={editDialogOpen}
            onOpenChange={setEditDialogOpen}
            mode="edit"
            initial={{ id: data.category.id, name: data.category.name, emoji: data.category.emoji }}
            onSaved={handleRenamed}
            // This page's own layout switches to the phone one at 1024px (Tailwind's lg:),
            // not the app-wide 768px MOBILE_BREAKPOINT the dialog defaults to.
            mobileBreakpoint={1024}
          />
          <CategoryDeleteFlow
            open={deleteDialogOpen}
            onOpenChange={setDeleteDialogOpen}
            categoryId={data.category.id}
            categoryName={data.category.name}
            onDeleted={handleDeleted}
          />
        </>
      )}
    </div>
  );
}

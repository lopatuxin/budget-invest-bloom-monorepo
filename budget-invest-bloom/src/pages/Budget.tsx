import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { useToast } from '@/hooks/use-toast';
import { useOperationDialog } from '@/components/operation/OperationDialogProvider';
import { useBudgetSummary } from '@/hooks/useBudgetSummary';
import { useBudgetOperations } from '@/hooks/useBudgetOperations';
import { BudgetMonthHeader } from '@/pages/budget/BudgetMonthHeader';
import { BudgetSummaryStrip } from '@/pages/budget/BudgetSummaryStrip';
import { BudgetCategoryGrid } from '@/pages/budget/BudgetCategoryGrid';
import { BudgetOperationsFeed } from '@/pages/budget/BudgetOperationsFeed';
import { BudgetCategoryDialog } from '@/pages/budget/BudgetCategoryDialog';
import { getBudgetPeriodKind } from '@/pages/budget/budgetPeriod';

function BudgetErrorCard({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="glass-card p-6 flex flex-col items-center gap-3 text-center">
      <p className="text-sm text-app-text-muted">{message}</p>
      <Button
        variant="outline"
        size="sm"
        onClick={() => onRetry()}
        className="border-app-border-strong bg-app-surface text-app-text hover:bg-app-surface-2"
      >
        Повторить
      </Button>
    </div>
  );
}

const Budget = () => {
  const { toast } = useToast();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { openOperationDialog } = useOperationDialog();

  const now = new Date();
  const month = Number(searchParams.get('month')) || now.getMonth() + 1;
  const year = Number(searchParams.get('year')) || now.getFullYear();
  const periodKind = getBudgetPeriodKind(month, year);

  const [categoryDialogOpen, setCategoryDialogOpen] = useState(false);

  // ?action=category is still supported so links elsewhere can deep-link into the dialog.
  useEffect(() => {
    if (searchParams.get('action') === 'category') {
      setCategoryDialogOpen(true);
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          next.delete('action');
          return next;
        },
        { replace: true }
      );
    }
  }, [searchParams, setSearchParams]);

  const {
    data: summaryData,
    isLoading: summaryLoading,
    error: summaryError,
    refetch: refetchSummary,
  } = useBudgetSummary(String(month), String(year));
  const {
    data: operationsData,
    isLoading: operationsLoading,
    error: operationsError,
    refetch: refetchOperations,
  } = useBudgetOperations(String(month), String(year));

  useEffect(() => {
    if (summaryError) {
      toast({
        title: 'Ошибка загрузки',
        description: summaryError instanceof Error ? summaryError.message : 'Не удалось загрузить сводку бюджета',
        variant: 'destructive',
      });
    }
  }, [summaryError, toast]);

  useEffect(() => {
    if (operationsError) {
      toast({
        title: 'Ошибка загрузки',
        description: operationsError instanceof Error ? operationsError.message : 'Не удалось загрузить операции',
        variant: 'destructive',
      });
    }
  }, [operationsError, toast]);

  const summary = summaryData?.body;
  const operations = operationsData?.body;

  const goToMonth = (nextMonth: number, nextYear: number) => {
    setSearchParams({ month: String(nextMonth), year: String(nextYear) });
  };

  const handlePrevMonth = () => (month === 1 ? goToMonth(12, year - 1) : goToMonth(month - 1, year));
  const handleNextMonth = () => (month === 12 ? goToMonth(1, year + 1) : goToMonth(month + 1, year));

  return (
    <div className="flex flex-col gap-5 pb-6">
      <BudgetMonthHeader
        month={month}
        year={year}
        dayOfMonth={summary?.dayOfMonth ?? 0}
        daysInMonth={summary?.daysInMonth ?? 30}
        periodKind={periodKind}
        isLoading={summaryLoading}
        onPrevMonth={handlePrevMonth}
        onNextMonth={handleNextMonth}
        onOpenCategoryDialog={() => setCategoryDialogOpen(true)}
      />

      {summaryLoading ? (
        <Skeleton className="h-[150px] bg-app-border" />
      ) : summaryError ? (
        <BudgetErrorCard message="Не удалось загрузить сводку" onRetry={refetchSummary} />
      ) : summary ? (
        <BudgetSummaryStrip
          expenses={summary.expenses}
          expenseNorm={summary.expenseNorm}
          income={summary.income}
          incomeNorm={summary.incomeNorm}
          balance={summary.balance}
          dayOfMonth={summary.dayOfMonth}
          periodKind={periodKind}
        />
      ) : null}

      <div className="grid grid-cols-1 xl:grid-cols-[1fr_400px] gap-5 items-start">
        {summaryLoading ? (
          <Skeleton className="h-[420px] bg-app-border" />
        ) : summaryError ? (
          <div className="hidden xl:block" />
        ) : summary ? (
          <BudgetCategoryGrid
            categories={summary.categories}
            dayOfMonth={summary.dayOfMonth}
            periodKind={periodKind}
            onCategoryClick={(name) => navigate(`/budget/category/${encodeURIComponent(name)}`)}
            onCreateCategory={() => setCategoryDialogOpen(true)}
          />
        ) : null}

        {operationsLoading ? (
          <Skeleton className="h-[420px] bg-app-border" />
        ) : operationsError ? (
          <BudgetErrorCard message="Не удалось загрузить операции" onRetry={refetchOperations} />
        ) : operations ? (
          <BudgetOperationsFeed
            operations={operations.items}
            total={operations.total}
            onEmptyAction={() => openOperationDialog('expense')}
          />
        ) : null}
      </div>

      <BudgetCategoryDialog open={categoryDialogOpen} onOpenChange={setCategoryDialogOpen} />
    </div>
  );
};

export default Budget;

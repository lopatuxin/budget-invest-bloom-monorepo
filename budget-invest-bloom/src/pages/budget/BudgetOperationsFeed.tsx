import { useMemo, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { ArrowDownLeft, Inbox, Trash2 } from 'lucide-react';
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import EmptyState from '@/components/EmptyState';
import { useToast } from '@/hooks/use-toast';
import { useDeleteExpense } from '@/hooks/useDeleteExpense';
import { useDeleteIncome } from '@/hooks/useDeleteIncome';
import { ApiError } from '@/lib/api';
import { invalidateBudgetCaches } from '@/lib/queryKeys';
import { formatCurrency, formatRelativeDay } from '@/lib/dateOptions';
import type { Operation } from '@/types/budget';

function parseApiDate(value: string): Date {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, month - 1, day);
}

function pluralOperations(n: number): string {
  const mod10 = n % 10;
  const mod100 = n % 100;
  if (mod10 === 1 && mod100 !== 11) return `${n} операция`;
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return `${n} операции`;
  return `${n} операций`;
}

interface OperationRowProps {
  operation: Operation;
  onDeleteRequest: (operation: Operation) => void;
}

function OperationRow({ operation, onDeleteRequest }: OperationRowProps) {
  const isExpense = operation.kind === 'EXPENSE';
  const title = operation.description || (isExpense ? operation.categoryName : operation.sourceName) || '';
  const subtitle = isExpense ? operation.categoryName : `Доход · ${operation.sourceName}`;
  const showSubtitle = isExpense ? Boolean(operation.description) : true;

  return (
    <div className="group flex items-center gap-3 h-10 py-1">
      <div className="w-8 h-8 rounded-lg bg-app-surface-2 flex items-center justify-center text-base shrink-0">
        {isExpense ? (
          operation.categoryEmoji || operation.categoryName?.charAt(0).toUpperCase()
        ) : (
          <ArrowDownLeft aria-hidden="true" className="w-4 h-4 text-app-good" />
        )}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm text-app-text truncate">{title}</p>
        {showSubtitle && <p className="text-xs text-app-text-muted truncate">{subtitle}</p>}
      </div>
      <span className={`font-mono text-[13px] shrink-0 ${isExpense ? 'text-app-text' : 'text-app-good font-semibold'}`}>
        {isExpense ? '−' : '+'}
        {formatCurrency(operation.amount)}
      </span>
      <button
        type="button"
        aria-label="Удалить операцию"
        onClick={() => onDeleteRequest(operation)}
        className="shrink-0 flex items-center justify-center w-11 h-11 -mr-2 lg:w-8 lg:h-8 lg:mr-0 rounded-lg text-app-text-muted opacity-100 lg:opacity-0 lg:group-hover:opacity-100 hover:text-app-bad hover:bg-app-bad-soft transition-opacity"
      >
        <Trash2 aria-hidden="true" className="w-4 h-4" />
      </button>
    </div>
  );
}

interface BudgetOperationsFeedProps {
  operations: Operation[];
  total: number;
  onEmptyAction: () => void;
}

// Grouped by date in the order the backend already sorted (date desc, then createdAt
// desc) — this component only labels the groups, it does not re-sort.
export function BudgetOperationsFeed({ operations, total, onEmptyAction }: BudgetOperationsFeedProps) {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const deleteExpense = useDeleteExpense();
  const deleteIncome = useDeleteIncome();
  const [pendingDelete, setPendingDelete] = useState<Operation | null>(null);

  const groups = useMemo(() => {
    const order: string[] = [];
    const byDate = new Map<string, Operation[]>();
    for (const operation of operations) {
      if (!byDate.has(operation.date)) {
        order.push(operation.date);
        byDate.set(operation.date, []);
      }
      byDate.get(operation.date)!.push(operation);
    }
    return order.map((date) => ({ date, label: formatRelativeDay(parseApiDate(date)), items: byDate.get(date)! }));
  }, [operations]);

  const isDeleting = deleteExpense.isPending || deleteIncome.isPending;

  const handleDeleteError = (error: unknown) => {
    if (error instanceof ApiError && error.status === 404) {
      toast({ title: 'Операция уже удалена' });
      invalidateBudgetCaches(queryClient);
      return;
    }
    const message = error instanceof Error ? error.message : 'Не удалось удалить операцию';
    toast({ variant: 'destructive', title: 'Ошибка', description: message });
  };

  const handleConfirmDelete = () => {
    if (!pendingDelete) return;
    const options = {
      onSuccess: () => toast({ title: 'Операция удалена' }),
      onError: handleDeleteError,
      onSettled: () => setPendingDelete(null),
    };
    if (pendingDelete.kind === 'EXPENSE') {
      deleteExpense.mutate({ expenseId: pendingDelete.id }, options);
    } else {
      deleteIncome.mutate({ incomeId: pendingDelete.id }, options);
    }
  };

  return (
    <div className="glass-card p-5">
      <div className="flex items-center justify-between mb-4">
        <h2 className="font-display text-[22px] text-app-text">Операции</h2>
        <span className="text-xs text-app-text-dim">{pluralOperations(total)} за месяц</span>
      </div>

      {groups.length === 0 ? (
        <EmptyState
          icon={<Inbox className="w-10 h-10" />}
          title="За этот месяц операций нет"
          description="Запишите первую операцию, чтобы увидеть её здесь"
          actionLabel="Записать расход"
          onAction={onEmptyAction}
        />
      ) : (
        <div className="flex flex-col gap-4">
          {groups.map((group) => (
            <div key={group.date}>
              <p className="text-[11px] uppercase tracking-[0.06em] text-app-text-dim mb-1">{group.label}</p>
              <div className="flex flex-col divide-y divide-app-border">
                {group.items.map((operation) => (
                  <OperationRow key={operation.id} operation={operation} onDeleteRequest={setPendingDelete} />
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      <AlertDialog open={pendingDelete !== null} onOpenChange={(open) => !open && setPendingDelete(null)}>
        <AlertDialogContent className="bg-app-surface border-app-border text-app-text">
          <AlertDialogHeader>
            <AlertDialogTitle>Удалить операцию?</AlertDialogTitle>
            <AlertDialogDescription className="text-app-text-muted">
              {pendingDelete && (
                <>
                  {formatCurrency(pendingDelete.amount)} ·{' '}
                  {pendingDelete.description ||
                    (pendingDelete.kind === 'EXPENSE' ? pendingDelete.categoryName : pendingDelete.sourceName)}
                </>
              )}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel className="border-app-border-strong bg-app-surface text-app-text hover:bg-app-surface-2">
              Отмена
            </AlertDialogCancel>
            <Button
              onClick={handleConfirmDelete}
              disabled={isDeleting}
              className="bg-app-bad text-white hover:bg-app-bad/90"
            >
              {isDeleting ? 'Удаление...' : 'Удалить'}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

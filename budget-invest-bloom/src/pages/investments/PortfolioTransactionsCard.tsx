import { useState } from 'react';
import { Trash2 } from 'lucide-react';
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
import { SecurityLogo } from '@/components/SecurityLogo';
import { useToast } from '@/hooks/use-toast';
import { useTransactions } from '@/hooks/useTransactions';
import { useDeleteTransaction } from '@/hooks/useDeleteTransaction';
import { formatCurrency, formatQuantity, formatUnitPrice } from '@/lib/dateOptions';
import { formatInstantDayMonth, pluralTransactions } from '@/pages/investments/investmentsFormat';
import type { TransactionResponse } from '@/types/investment';

interface PendingDelete {
  id: string;
  ticker: string;
  label: string;
}

interface PortfolioTransactionsCardProps {
  recentTransactions: TransactionResponse[];
  transactionsTotal: number;
}

export function PortfolioTransactionsCard({ recentTransactions, transactionsTotal }: PortfolioTransactionsCardProps) {
  const { toast } = useToast();
  const [expanded, setExpanded] = useState(false);
  const { data: allTransactions, isLoading: isLoadingAll } = useTransactions(undefined, expanded);
  const { mutate: deleteTransaction, isPending: isDeleting } = useDeleteTransaction();
  const [pendingDelete, setPendingDelete] = useState<PendingDelete | null>(null);

  const items: TransactionResponse[] = expanded ? allTransactions?.body ?? [] : recentTransactions;

  const handleConfirmDelete = () => {
    if (!pendingDelete) return;
    deleteTransaction(
      { id: pendingDelete.id, ticker: pendingDelete.ticker },
      {
        onSuccess: () => toast({ title: 'Сделка удалена' }),
        onSettled: () => setPendingDelete(null),
      }
    );
  };

  return (
    <div className="glass-card p-4 lg:p-5 flex flex-col">
      <div className="flex items-baseline justify-between mb-1.5">
        <h2 className="font-display text-[20px] lg:text-[22px] text-app-text">Сделки</h2>
        <span className="text-app-text-dim text-xs">{pluralTransactions(transactionsTotal)} за всё время</span>
      </div>

      {items.length === 0 ? (
        <p className="text-app-text-muted text-sm py-4">сделок пока нет</p>
      ) : (
        <div className="flex flex-col divide-y divide-app-border">
          {items.map((tx) => {
            const isBuy = tx.type === 'BUY';
            const label = isBuy ? 'Покупка' : tx.type === 'REDEMPTION' ? 'Погашение' : 'Продажа';
            const canDelete = tx.type !== 'REDEMPTION';
            return (
              <div key={tx.id} className="group flex items-center gap-2.5 h-11">
                <span
                  className={`font-mono text-[11px] font-semibold h-5 px-1.5 rounded shrink-0 flex items-center ${
                    isBuy ? 'bg-app-good-soft text-app-good' : 'bg-app-bad-soft text-app-bad'
                  }`}
                >
                  {label}
                </span>
                <SecurityLogo ticker={tx.ticker} size={28} />
                <div className="flex-1 min-w-0 truncate">
                  <span className="font-mono text-sm font-semibold text-app-text">{tx.ticker}</span>
                  <span className="text-app-text-muted text-xs ml-2">
                    {formatQuantity(tx.quantity)} × {formatUnitPrice(tx.price)}
                  </span>
                </div>
                <span className="font-mono text-[13px] text-app-text shrink-0">{formatCurrency(tx.amount)}</span>
                <span className="text-app-text-dim text-xs w-[92px] text-right shrink-0">{formatInstantDayMonth(tx.executedAt)}</span>
                {canDelete ? (
                  <button
                    type="button"
                    aria-label="Удалить сделку"
                    onClick={() => setPendingDelete({ id: tx.id, ticker: tx.ticker, label: `${formatCurrency(tx.amount)} · ${tx.ticker}` })}
                    className="shrink-0 flex items-center justify-center w-8 h-8 -mr-1 rounded-lg text-app-text-muted opacity-100 lg:opacity-0 lg:group-hover:opacity-100 hover:text-app-bad hover:bg-app-bad-soft transition-opacity"
                  >
                    <Trash2 aria-hidden="true" className="w-4 h-4" />
                  </button>
                ) : (
                  <span aria-hidden="true" className="shrink-0 w-8 h-8 -mr-1" />
                )}
              </div>
            );
          })}
        </div>
      )}

      {!expanded && transactionsTotal > recentTransactions.length && (
        <button type="button" onClick={() => setExpanded(true)} className="text-app-accent text-[13px] mt-3 text-left w-fit hover:underline">
          все сделки
        </button>
      )}
      {expanded && isLoadingAll && <p className="text-app-text-dim text-xs mt-3">Загрузка...</p>}

      <AlertDialog open={pendingDelete !== null} onOpenChange={(open) => !open && setPendingDelete(null)}>
        <AlertDialogContent className="bg-app-surface border-app-border text-app-text">
          <AlertDialogHeader>
            <AlertDialogTitle>Удалить сделку?</AlertDialogTitle>
            <AlertDialogDescription className="text-app-text-muted">{pendingDelete?.label}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel className="border-app-border-strong bg-app-surface text-app-text hover:bg-app-surface-2">
              Отмена
            </AlertDialogCancel>
            <Button onClick={handleConfirmDelete} disabled={isDeleting} className="bg-app-bad text-white hover:bg-app-bad/90">
              {isDeleting ? 'Удаление...' : 'Удалить'}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

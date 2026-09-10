import { useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { useToast } from '@/hooks/use-toast';
import { useDeleteManualDividend } from '@/hooks/useDeleteManualDividend';
import { formatDayMonth, formatDividendAmount, formatDividendDateLabel, isUpcomingDividend, upcomingDividendSortDate } from '@/lib/dateOptions';
import { SecurityDividendDialog } from '@/pages/security/SecurityDividendDialog';
import type { SecurityDividend } from '@/types/investment';

interface PendingDelete {
  id: string;
  label: string;
}

interface DividendRowProps {
  dividend: SecurityDividend;
  variant: 'upcoming' | 'received';
  onDeleteRequest: (dividend: SecurityDividend) => void;
}

function DividendRow({ dividend, variant, onDeleteRequest }: DividendRowProps) {
  const isManual = dividend.source === 'MANUAL';
  return (
    <div className="group flex items-center gap-2.5 p-3 bg-white/[0.03] rounded-lg">
      {variant === 'upcoming' && (
        <span className="text-[11px] font-semibold px-1.5 py-0.5 rounded bg-emerald-500/10 text-emerald-400 shrink-0">
          скоро
        </span>
      )}
      <span className="text-sm font-mono text-dashboard-text flex-1">
        {formatDividendAmount(dividend.amountPerShare, dividend.currency, 'unit')} на акцию
      </span>
      {isManual && <span className="text-xs text-dashboard-text-muted shrink-0">вручную</span>}
      <span className="text-sm text-dashboard-text-muted shrink-0">{formatDividendDateLabel(dividend, variant)}</span>
      {isManual && (
        <button
          type="button"
          aria-label="Удалить дивиденд"
          onClick={() => onDeleteRequest(dividend)}
          className="shrink-0 flex items-center justify-center w-7 h-7 rounded-lg text-dashboard-text-muted opacity-100 lg:opacity-0 lg:group-hover:opacity-100 hover:text-red-400 hover:bg-red-500/10 transition-opacity"
        >
          <Trash2 aria-hidden="true" className="w-4 h-4" />
        </button>
      )}
    </div>
  );
}

interface SecurityDividendsCardProps {
  ticker: string;
  dividends: SecurityDividend[];
  isLoading: boolean;
}

export function SecurityDividendsCard({ ticker, dividends, isLoading }: SecurityDividendsCardProps) {
  const { toast } = useToast();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<PendingDelete | null>(null);
  const { mutate: deleteDividend, isPending: isDeleting } = useDeleteManualDividend();

  const upcoming = dividends.filter(isUpcomingDividend);
  const received = dividends.filter((dividend) => !isUpcomingDividend(dividend));

  // Upcoming rows read by the nearest applicable date — recordDate-desc order
  // from the backend doesn't match that, so it's re-sorted here for display only.
  const sortedUpcoming = [...upcoming].sort((a, b) => upcomingDividendSortDate(a).localeCompare(upcomingDividendSortDate(b)));

  const handleDeleteRequest = (dividend: SecurityDividend) => {
    setPendingDelete({
      id: dividend.id,
      label: `${formatDividendAmount(dividend.amountPerShare, dividend.currency, 'unit')} · ${formatDayMonth(dividend.recordDate)}`,
    });
  };

  const handleConfirmDelete = () => {
    if (!pendingDelete) return;
    deleteDividend(
      { dividendId: pendingDelete.id, ticker },
      {
        onSuccess: () => toast({ title: 'Дивиденд удалён' }),
        onSettled: () => setPendingDelete(null),
      }
    );
  };

  return (
    <div className="glass-card p-5 animate-fade-slide-up" style={{ animationDelay: '180ms' }}>
      <div className="flex items-center justify-between mb-1">
        <h3 className="text-sm font-semibold text-dashboard-text">История дивидендов</h3>
        <Button type="button" size="sm" variant="outline" onClick={() => setDialogOpen(true)}>
          <Plus className="w-4 h-4 mr-1.5" />
          Добавить вручную
        </Button>
      </div>
      <p className="text-dashboard-text-muted text-xs mb-4">суммы на акцию до налога</p>

      {isLoading ? (
        <div className="space-y-2">
          {[...Array(3)].map((_, i) => (
            <Skeleton key={i} className="h-10" />
          ))}
        </div>
      ) : dividends.length === 0 ? (
        <p className="text-dashboard-text-muted text-sm">Нет выплаченных дивидендов</p>
      ) : (
        <div className="space-y-2">
          {sortedUpcoming.map((dividend) => (
            <DividendRow key={dividend.id} dividend={dividend} variant="upcoming" onDeleteRequest={handleDeleteRequest} />
          ))}
          {received.map((dividend) => (
            <DividendRow key={dividend.id} dividend={dividend} variant="received" onDeleteRequest={handleDeleteRequest} />
          ))}
        </div>
      )}

      <SecurityDividendDialog ticker={ticker} open={dialogOpen} onOpenChange={setDialogOpen} />

      <AlertDialog open={pendingDelete !== null} onOpenChange={(open) => !open && setPendingDelete(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Удалить дивиденд?</AlertDialogTitle>
            <AlertDialogDescription>{pendingDelete?.label}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Отмена</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleConfirmDelete}
              disabled={isDeleting}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              {isDeleting ? 'Удаление...' : 'Удалить'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

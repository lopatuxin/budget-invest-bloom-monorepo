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
import { useToast } from '@/hooks/use-toast';
import { useDeleteTransaction } from '@/hooks/useDeleteTransaction';
import { useDeleteManualDividend } from '@/hooks/useDeleteManualDividend';
import { SecurityDividendDialog } from '@/pages/security/SecurityDividendDialog';
import { describeEvent, formatEventDate, pluralEvents, SECURITY_BUY_COLOR, SECURITY_SELL_COLOR } from '@/pages/security/securityFormat';
import { PORTFOLIO_CHART_COLOR } from '@/pages/investments/investmentsFormat';
import type { SecurityEvent } from '@/types/investment';

type PendingDelete = { kind: 'transaction' | 'dividend'; id: string; label: string };

const DOT_COLOR: Record<'BUY' | 'SELL' | 'DIVIDEND_PAID', string> = {
  BUY: SECURITY_BUY_COLOR,
  SELL: SECURITY_SELL_COLOR,
  DIVIDEND_PAID: PORTFOLIO_CHART_COLOR,
};

function eventKey(event: SecurityEvent): string {
  return event.kind === 'BUY' || event.kind === 'SELL' ? event.transactionId : event.dividendId;
}

interface SecurityTimelineProps {
  ticker: string;
  events: SecurityEvent[];
}

export function SecurityTimeline({ ticker, events }: SecurityTimelineProps) {
  const { toast } = useToast();
  const [dividendDialogOpen, setDividendDialogOpen] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<PendingDelete | null>(null);
  const { mutate: deleteTransaction, isPending: isDeletingTransaction } = useDeleteTransaction();
  const { mutate: deleteDividend, isPending: isDeletingDividend } = useDeleteManualDividend();
  const isDeleting = isDeletingTransaction || isDeletingDividend;

  const handleConfirmDelete = () => {
    if (!pendingDelete) return;
    if (pendingDelete.kind === 'transaction') {
      deleteTransaction(
        { id: pendingDelete.id, ticker },
        { onSuccess: () => toast({ title: 'Сделка удалена' }), onSettled: () => setPendingDelete(null) }
      );
    } else {
      deleteDividend(
        { dividendId: pendingDelete.id, ticker },
        { onSuccess: () => toast({ title: 'Дивиденд удалён' }), onSettled: () => setPendingDelete(null) }
      );
    }
  };

  return (
    <div className="glass-card px-3.5 pt-3 pb-2 lg:p-5 lg:pb-3">
      <div className="flex items-baseline justify-between mb-1">
        <span className="font-display text-[18px] lg:text-[22px] text-app-text">Что было с бумагой</span>
        <span className="lg:hidden text-app-text-dim text-[11px]">{pluralEvents(events.length)}</span>
        <span className="hidden lg:inline text-app-text-dim text-xs">сделки и дивиденды одной лентой</span>
      </div>

      <div className="relative">
        {/* Desktop timeline rail (p.11): passes behind the dots, which sit above it as later positioned siblings */}
        <span aria-hidden="true" className="hidden lg:block absolute left-[5.5px] top-[22px] bottom-6 w-px bg-app-border-strong" />
        <div className="flex flex-col divide-y divide-app-border">
          {events.map((event) => {
            const display = describeEvent(event);
            const isUpcoming = event.kind === 'DIVIDEND_UPCOMING';
            const canDelete = event.kind === 'BUY' || event.kind === 'SELL' || (event.kind === 'DIVIDEND_PAID' && event.source === 'MANUAL');
            const key = eventKey(event);
            const date = formatEventDate(event.date);

            return (
              <div key={`${event.kind}-${key}`} className="group flex items-start gap-2.5 lg:gap-3 py-2.5 lg:py-3">
                <span
                  className="relative w-2.5 h-2.5 lg:w-3 lg:h-3 rounded-full mt-1 shrink-0 border-2 border-app-surface bg-app-surface"
                  style={
                    isUpcoming
                      ? { boxShadow: '0 0 0 1.5px rgb(var(--app-accent))' }
                      : { backgroundColor: DOT_COLOR[event.kind], boxShadow: '0 0 0 1.5px rgb(var(--app-border-strong))' }
                  }
                />
                <span className="hidden lg:block text-app-text-muted text-xs w-[110px] shrink-0 pt-0.5">{date}</span>
                <div className="flex-1 min-w-0">
                  <p className="lg:hidden text-app-text-dim text-[11px]">{date}</p>
                  <p className="text-app-text text-[13px] font-medium">{display.title}</p>
                  <p className="text-app-text-muted text-[11px] lg:text-xs mt-0.5">{display.subtitle}</p>
                </div>
                <div className="flex items-center gap-2 shrink-0">
                  {isUpcoming && (
                    <span className="h-5 px-1.5 rounded-full bg-app-accent-soft text-app-accent text-[11px] inline-flex items-center shrink-0">
                      скоро
                    </span>
                  )}
                  <span
                    className={`font-mono font-semibold text-[13px] whitespace-nowrap ${
                      display.amountVariant === 'good' ? 'text-app-good' : display.amountVariant === 'dim' ? 'text-app-text-dim' : 'text-app-text'
                    }`}
                  >
                    {display.amountText}
                  </span>
                  {canDelete && (
                    <button
                      type="button"
                      aria-label={event.kind === 'DIVIDEND_PAID' ? 'Удалить дивиденд' : 'Удалить сделку'}
                      onClick={() =>
                        setPendingDelete(
                          event.kind === 'DIVIDEND_PAID'
                            ? { kind: 'dividend', id: key, label: display.title }
                            : { kind: 'transaction', id: key, label: display.title }
                        )
                      }
                      className="flex items-center justify-center w-11 h-11 -my-3 -mr-2.5 lg:w-7 lg:h-7 lg:my-0 lg:mr-0 rounded-lg text-app-text-muted opacity-100 lg:opacity-0 lg:group-hover:opacity-100 lg:focus-visible:opacity-100 hover:text-app-bad hover:bg-app-bad-soft transition-opacity"
                    >
                      <Trash2 aria-hidden="true" className="w-4 h-4" />
                    </button>
                  )}
                  {/* Keeps amounts in one column on desktop, where the trash button is hover-only */}
                  {!canDelete && <span aria-hidden="true" className="hidden lg:block w-7 shrink-0" />}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      <button
        type="button"
        onClick={() => setDividendDialogOpen(true)}
        className="text-app-accent text-[13px] min-h-[44px] lg:min-h-0 lg:mt-2 lg:mb-1 text-left w-fit hover:underline"
      >
        добавить дивиденд вручную
      </button>

      <SecurityDividendDialog ticker={ticker} open={dividendDialogOpen} onOpenChange={setDividendDialogOpen} />

      <AlertDialog open={pendingDelete !== null} onOpenChange={(open) => !open && setPendingDelete(null)}>
        <AlertDialogContent className="bg-app-surface border-app-border text-app-text">
          <AlertDialogHeader>
            <AlertDialogTitle>{pendingDelete?.kind === 'dividend' ? 'Удалить дивиденд?' : 'Удалить сделку?'}</AlertDialogTitle>
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

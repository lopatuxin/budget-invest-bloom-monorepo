import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { SearchX } from 'lucide-react';
import { Skeleton } from '@/components/ui/skeleton';
import { RetryErrorCard } from '@/components/RetryErrorCard';
import EmptyState from '@/components/EmptyState';
import { TransactionDialog } from '@/components/investments/TransactionDialog';
import { useToast } from '@/hooks/use-toast';
import { useSecurityPage } from '@/hooks/useSecurityPage';
import { ApiError } from '@/lib/api';
import { SecurityHeader } from '@/pages/security/SecurityHeader';
import { SecurityPriceCard } from '@/pages/security/SecurityPriceCard';
import { SecurityTimeline } from '@/pages/security/SecurityTimeline';
import { SecuritySummaryCard } from '@/pages/security/SecuritySummaryCard';
import { SecuritySummaryCompactCard } from '@/pages/security/SecuritySummaryCompactCard';
import type { MoexSecuritySearchItem } from '@/types/investment';

function SecurityPageSkeleton() {
  return (
    <div className="flex flex-col gap-4 lg:gap-5">
      <Skeleton className="h-12 bg-app-border" />
      <Skeleton className="h-[300px] bg-app-border" />
      <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_380px] gap-4 lg:gap-5">
        <Skeleton className="h-[420px] bg-app-border" />
        <Skeleton className="h-[260px] bg-app-border" />
      </div>
    </div>
  );
}

export function SecurityPage() {
  const { ticker: rawTicker } = useParams<{ ticker: string }>();
  const ticker = rawTicker ? rawTicker.toUpperCase() : null;
  const { toast } = useToast();
  const [dialogOpen, setDialogOpen] = useState(false);

  const { data, isLoading, error, refetch } = useSecurityPage(ticker);
  const page = data?.body;
  const isNotFound = error instanceof ApiError && error.status === 404;

  useEffect(() => {
    if (error && !isNotFound) {
      toast({
        title: 'Ошибка загрузки',
        description: error instanceof Error ? error.message : 'Не удалось загрузить бумагу',
        variant: 'destructive',
      });
    }
  }, [error, isNotFound, toast]);

  const initialSecurity: MoexSecuritySearchItem | undefined = page
    ? {
        ticker: page.security.ticker,
        boardId: page.security.boardId ?? '',
        name: page.security.name,
        securityType: page.security.securityType,
        sector: page.security.sector ?? null,
        currency: null,
      }
    : undefined;
  const quantityByTicker = page?.position ? { [page.security.ticker]: page.position.quantity } : undefined;

  return (
    <div className="flex flex-col gap-3 lg:gap-5 pb-20 lg:pb-6">
      <TransactionDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        initialSecurity={initialSecurity}
        quantityByTicker={quantityByTicker}
      />

      {isLoading ? (
        <SecurityPageSkeleton />
      ) : isNotFound ? (
        <div className="glass-card p-8">
          <EmptyState
            icon={<SearchX className="w-12 h-12" />}
            title={`Бумаги ${ticker ?? ''} в портфеле нет`}
            description="сделок по ней ещё не было"
            actionLabel="Записать сделку"
            onAction={() => setDialogOpen(true)}
          />
        </div>
      ) : error || !page ? (
        <RetryErrorCard message="Не удалось загрузить бумагу" onRetry={refetch} />
      ) : (
        <>
          <SecurityHeader security={page.security} price={page.price} onOpenTransactionDialog={() => setDialogOpen(true)} />
          <SecurityPriceCard
            ticker={page.security.ticker}
            price={page.price}
            markers={page.markers}
            averagePrice={page.position?.averagePrice ?? null}
          />
          <SecuritySummaryCompactCard position={page.position} dividends={page.dividends} result={page.result} />
          <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_380px] gap-4 lg:gap-5 items-start">
            <SecurityTimeline ticker={page.security.ticker} events={page.events} />
            <SecuritySummaryCard position={page.position} dividends={page.dividends} result={page.result} />
          </div>
        </>
      )}
    </div>
  );
}

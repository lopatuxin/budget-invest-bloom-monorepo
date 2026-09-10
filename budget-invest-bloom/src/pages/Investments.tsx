import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { BarChart2 } from 'lucide-react';
import { Skeleton } from '@/components/ui/skeleton';
import { RetryErrorCard } from '@/components/RetryErrorCard';
import AddAssetDialog from '@/components/AddAssetDialog';
import EmptyState from '@/components/EmptyState';
import { useToast } from '@/hooks/use-toast';
import { useInvestmentPortfolio } from '@/hooks/useInvestmentPortfolio';
import { InvestmentsHeader } from '@/pages/investments/InvestmentsHeader';
import { PortfolioValueCard } from '@/pages/investments/PortfolioValueCard';
import { PortfolioAllocationCard } from '@/pages/investments/PortfolioAllocationCard';
import { PortfolioGroups } from '@/pages/investments/PortfolioGroups';
import { PortfolioTransactionsCard } from '@/pages/investments/PortfolioTransactionsCard';
import { PortfolioDividendsCard } from '@/pages/investments/PortfolioDividendsCard';
import type { PortfolioSort } from '@/types/investment';

function InvestmentsLoadingSkeleton() {
  return (
    <div className="flex flex-col gap-4 lg:gap-5">
      <Skeleton className="h-[220px] lg:h-[260px] bg-app-border" />
      <Skeleton className="h-[80px] bg-app-border" />
      <Skeleton className="h-[420px] bg-app-border" />
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 lg:gap-5">
        <Skeleton className="h-[280px] bg-app-border" />
        <Skeleton className="h-[280px] bg-app-border" />
      </div>
    </div>
  );
}

const Investments = () => {
  const { toast } = useToast();
  const [sort, setSort] = useState<PortfolioSort>('WEIGHT');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [searchParams, setSearchParams] = useSearchParams();
  const { data, isLoading, error, refetch } = useInvestmentPortfolio(sort);
  const portfolio = data?.body;

  // Open add-transaction dialog when navigated with ?action=add-asset
  const actionParam = searchParams.get('action');
  useEffect(() => {
    if (actionParam === 'add-asset') {
      setDialogOpen(true);
      setSearchParams({}, { replace: true });
    }
  }, [actionParam, setSearchParams]);

  useEffect(() => {
    if (error) {
      toast({
        title: 'Ошибка загрузки',
        description: error instanceof Error ? error.message : 'Не удалось загрузить портфель',
        variant: 'destructive',
      });
    }
  }, [error, toast]);

  const isEmpty = Boolean(portfolio && portfolio.overview.assetsCount === 0);

  return (
    <div className="flex flex-col gap-4 lg:gap-5 pb-6">
      <AddAssetDialog open={dialogOpen} onOpenChange={setDialogOpen} />

      <InvestmentsHeader
        pricesAsOf={portfolio?.overview.pricesAsOf ?? null}
        pricesStale={portfolio?.overview.pricesStale ?? false}
        onAddTransaction={() => setDialogOpen(true)}
      />

      {isLoading ? (
        <InvestmentsLoadingSkeleton />
      ) : error || !portfolio ? (
        <RetryErrorCard message="Не удалось загрузить портфель" onRetry={refetch} />
      ) : (
        <>
          <PortfolioValueCard overview={portfolio.overview} isEmpty={isEmpty} onAddTransaction={() => setDialogOpen(true)} />

          {isEmpty ? (
            <div className="glass-card p-8">
              <EmptyState
                icon={<BarChart2 className="w-12 h-12" />}
                title="Портфель пуст"
                description="Добавьте первую сделку, чтобы начать отслеживать инвестиции"
                actionLabel="Добавить сделку"
                onAction={() => setDialogOpen(true)}
              />
            </div>
          ) : (
            <>
              <PortfolioAllocationCard allocation={portfolio.allocation} />
              <PortfolioGroups groups={portfolio.groups} sort={sort} onSortChange={setSort} onAddTransaction={() => setDialogOpen(true)} />
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 lg:gap-5 items-start">
                <PortfolioTransactionsCard recentTransactions={portfolio.recentTransactions} transactionsTotal={portfolio.transactionsTotal} />
                <PortfolioDividendsCard
                  upcomingDividends={portfolio.upcomingDividends}
                  recentDividends={portfolio.recentDividends}
                  dividends12m={portfolio.overview.dividends12m}
                  dividendYieldPercent={portfolio.overview.dividendYieldPercent}
                  dividendTaxRatePercent={portfolio.overview.dividendTaxRatePercent}
                  dividendsSourceConfigured={portfolio.overview.dividendsSourceConfigured}
                />
              </div>
            </>
          )}
        </>
      )}
    </div>
  );
};

export default Investments;

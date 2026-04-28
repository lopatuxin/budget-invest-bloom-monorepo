import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { TrendingUp, PieChart, Coins, Trash2, BarChart2, Calculator } from 'lucide-react';
import AddAssetDialog from '@/components/AddAssetDialog';
import PortfolioValueChart from '@/components/PortfolioValueChart';
import SecurityPriceChart from '@/components/SecurityPriceChart';
import EmptyState from '@/components/EmptyState';
import { usePositions } from '@/hooks/usePositions';
import { useTransactions } from '@/hooks/useTransactions';
import { useDeleteTransaction } from '@/hooks/useDeleteTransaction';
import { useInvestmentOverview } from '@/hooks/useInvestmentOverview';
import { useToast } from '@/hooks/use-toast';

const DONUT_COLORS = ['#10B981', '#3B82F6', '#F59E0B', '#8B5CF6', '#EC4899', '#06B6D4', '#F97316', '#14B8A6'];
const formatCurrency = (value: number) => value.toLocaleString('ru-RU') + ' ₽';

// Animated count-up hook with easeOutCubic easing
const useCountUp = (target: number, duration = 800) => {
  const [value, setValue] = useState(0);
  useEffect(() => {
    if (target === 0) { setValue(0); return; }
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) { setValue(target); return; }
    let rafId: number;
    const start = performance.now();
    const step = (now: number) => {
      const progress = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      setValue(Math.round(target * eased));
      if (progress < 1) rafId = requestAnimationFrame(step);
    };
    rafId = requestAnimationFrame(step);
    return () => cancelAnimationFrame(rafId);
  }, [target, duration]);
  return value;
};

const pluralAssets = (n: number) => {
  const mod10 = n % 10;
  const mod100 = n % 100;
  if (mod10 === 1 && mod100 !== 11) return `${n} актив`;
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return `${n} актива`;
  return `${n} активов`;
};

const Skeleton = ({ className = '' }: { className?: string }) => (
  <div className={`animate-pulse bg-white/10 rounded-lg ${className}`} />
);

const Investments = () => {
  const { toast } = useToast();
  const { data: positionsData, isLoading: positionsLoading } = usePositions();
  const { data: transactionsData } = useTransactions();
  const { data: overviewData, isLoading: overviewLoading } = useInvestmentOverview();
  const { mutate: deleteTransaction } = useDeleteTransaction();
  const [dialogOpen, setDialogOpen] = useState(false);

  const isLoading = positionsLoading || overviewLoading;

  const positions = positionsData?.body ?? [];
  const transactions = transactionsData?.body ?? [];
  const overview = overviewData?.body;

  const animTotalValue = useCountUp(overview?.totalValue ?? 0);
  const animTotalPnl = useCountUp(overview?.totalPnl ?? 0);
  const animDailyPnl = useCountUp(overview?.dailyPnl ?? 0);

  const totalCost = overview?.totalCost ?? 0;
  const totalPnlPercent = totalCost > 0 && overview
    ? ((overview.totalPnl / totalCost) * 100).toFixed(1)
    : null;

  // Build sector aggregation from positions
  const totalValue = overview?.totalValue ?? positions.reduce((sum, p) => sum + p.totalCost, 0);

  const sectorMap = positions.reduce<Record<string, number>>((acc, p) => {
    const name = p.sector ?? 'Без сектора';
    acc[name] = (acc[name] ?? 0) + p.totalCost;
    return acc;
  }, {});

  const sectors = Object.entries(sectorMap).map(([name, value], index) => ({
    name,
    value,
    percentage: totalValue > 0 ? Math.round((value / totalValue) * 100) : 0,
    color: DONUT_COLORS[index % DONUT_COLORS.length],
  }));

  // Group positions by sector for the holdings list
  const positionsBySector = positions.reduce<Record<string, typeof positions>>((acc, p) => {
    const name = p.sector ?? 'Без сектора';
    if (!acc[name]) acc[name] = [];
    acc[name].push(p);
    return acc;
  }, {});

  // Color index per ticker position
  const holdingColorIndex = new Map<string, number>();
  positions.forEach((p, i) => holdingColorIndex.set(p.ticker, i));

  const handleDeleteTransaction = (id: string) => {
    deleteTransaction(id, {
      onSuccess: () => toast({ title: 'Сделка удалена' }),
      onError: (err) =>
        toast({
          title: 'Ошибка',
          description: err instanceof Error ? err.message : 'Не удалось удалить сделку',
          variant: 'destructive',
        }),
    });
  };

  const pnlColor = (overview?.totalPnl ?? 0) >= 0 ? '#10B981' : '#EF4444';

  const kpiCards = [
    {
      label: 'СТОИМОСТЬ ПОРТФЕЛЯ',
      value: isLoading ? null : formatCurrency(animTotalValue),
      icon: PieChart,
      color: '#3B82F6',
      glow: 'rgba(59, 130, 246, 0.3)',
    },
    {
      label: 'ОБЩАЯ ДОХОДНОСТЬ',
      value: isLoading ? null : overview
        ? `${animTotalPnl >= 0 ? '+' : ''}${formatCurrency(animTotalPnl)}${totalPnlPercent !== null ? ` (${totalPnlPercent}%)` : ''}`
        : '—',
      icon: TrendingUp,
      color: pnlColor,
      glow: (overview?.totalPnl ?? 0) >= 0 ? 'rgba(16, 185, 129, 0.3)' : 'rgba(239, 68, 68, 0.3)',
    },
    {
      label: 'ЗА ДЕНЬ',
      value: isLoading ? null : overview
        ? `${animDailyPnl >= 0 ? '+' : ''}${formatCurrency(animDailyPnl)}`
        : '—',
      icon: TrendingUp,
      color: '#8B5CF6',
      glow: 'rgba(139, 92, 246, 0.3)',
    },
    {
      label: 'ДИВИДЕНДЫ',
      value: isLoading ? null : formatCurrency(overview?.dividends12m ?? 0),
      icon: Coins,
      color: '#F59E0B',
      glow: 'rgba(245, 158, 11, 0.3)',
    },
  ];

  // Skeleton while loading
  if (isLoading) {
    return (
      <div className="space-y-6 pb-6">
        <div className="grid grid-cols-2 xl:grid-cols-4 gap-5">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-28" />
          ))}
        </div>
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
          <div className="lg:col-span-2 space-y-3">
            <Skeleton className="h-10 w-48" />
            {Array.from({ length: 5 }).map((_, i) => (
              <Skeleton key={i} className="h-16" />
            ))}
          </div>
          <Skeleton className="h-72" />
        </div>
      </div>
    );
  }

  // Empty state
  if (positions.length === 0) {
    return (
      <div className="space-y-6 pb-6">
        <div className="flex gap-4 overflow-x-auto snap-x snap-mandatory pb-2 -mx-4 px-4 lg:mx-0 lg:px-0 lg:grid lg:grid-cols-2 xl:grid-cols-4 lg:gap-5 lg:overflow-visible lg:pb-0 hide-scrollbar">
          {kpiCards.map((card, index) => {
            const Icon = card.icon;
            return (
              <div
                key={card.label}
                className="glass-card p-5 flex items-start justify-between animate-fade-slide-up min-w-[260px] snap-start lg:min-w-0"
                style={{ borderLeft: `3px solid ${card.color}`, animationDelay: `${index * 60}ms` }}
              >
                <div className="space-y-2">
                  <p className="text-[11px] font-semibold tracking-widest text-dashboard-text-muted">{card.label}</p>
                  <p className="text-2xl font-bold text-dashboard-text font-mono">
                    {card.value ?? <Skeleton className="h-8 w-32 inline-block" />}
                  </p>
                </div>
                <div
                  className="w-11 h-11 rounded-xl flex items-center justify-center shrink-0"
                  style={{ backgroundColor: `${card.color}20`, boxShadow: `0 0 20px ${card.glow}` }}
                >
                  <Icon className="w-5 h-5" style={{ color: card.color }} />
                </div>
              </div>
            );
          })}
        </div>

        <div className="glass-card p-8 animate-fade-slide-up" style={{ animationDelay: '300ms' }}>
          <EmptyState
            icon={<BarChart2 className="w-12 h-12" />}
            title="Портфель пуст"
            description="Добавьте первую сделку, чтобы начать отслеживать инвестиции"
            actionLabel="Добавить первую сделку"
            onAction={() => setDialogOpen(true)}
          />
        </div>

        {/* Controlled dialog opened from EmptyState action */}
        <AddAssetDialog open={dialogOpen} onOpenChange={setDialogOpen} />
      </div>
    );
  }

  return (
    <div className="space-y-6 pb-6">

      {/* Page header */}
      <div className="flex items-center justify-between animate-fade-slide-up">
        <h1 className="text-lg font-semibold text-dashboard-text">Мои инвестиции</h1>
        <Link
          to="/investments/calculator"
          className="flex items-center gap-2 px-4 py-2 bg-blue-500/10 border border-blue-500/20 text-blue-400 hover:bg-blue-500/20 hover:border-blue-500/40 transition-all duration-200 rounded-xl text-sm font-medium"
        >
          <Calculator className="w-4 h-4" />
          Калькулятор
        </Link>
      </div>

      {/* KPI Cards */}
      <div className="flex gap-4 overflow-x-auto snap-x snap-mandatory pb-2 -mx-4 px-4 lg:mx-0 lg:px-0 lg:grid lg:grid-cols-2 xl:grid-cols-4 lg:gap-5 lg:overflow-visible lg:pb-0 hide-scrollbar">
        {kpiCards.map((card, index) => {
          const Icon = card.icon;
          return (
            <div
              key={card.label}
              className="glass-card p-5 flex items-start justify-between group transition-all duration-300 hover:scale-[1.02] animate-fade-slide-up min-w-[260px] snap-start lg:min-w-0"
              style={{ borderLeft: `3px solid ${card.color}`, animationDelay: `${index * 60}ms` }}
            >
              <div className="space-y-2">
                <p className="text-[11px] font-semibold tracking-widest text-dashboard-text-muted">{card.label}</p>
                <p className="text-2xl font-bold text-dashboard-text font-mono">
                  {card.value ?? <Skeleton className="h-8 w-32 inline-block" />}
                </p>
              </div>
              <div
                className="w-11 h-11 rounded-xl flex items-center justify-center shrink-0"
                style={{ backgroundColor: `${card.color}20`, boxShadow: `0 0 20px ${card.glow}` }}
              >
                <Icon className="w-5 h-5" style={{ color: card.color }} />
              </div>
            </div>
          );
        })}
      </div>

      <PortfolioValueChart />

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        {/* Holdings + recent transactions */}
        <div className="lg:col-span-2 space-y-5">

          {/* My assets */}
          <div className="glass-card p-5 animate-fade-slide-up" style={{ animationDelay: '300ms' }}>
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-sm font-semibold text-dashboard-text">Мои активы</h3>
              <AddAssetDialog />
            </div>
            <div className="space-y-6 max-h-[400px] overflow-y-auto dashboard-scroll pr-1">
              {Object.entries(positionsBySector).map(([sectorName, sectorPositions], sectorIdx) => {
                const sectorValue = sectorPositions.reduce((sum, p) => sum + p.totalCost, 0);
                const sectorPercentage = totalValue > 0
                  ? ((sectorValue / totalValue) * 100).toFixed(1)
                  : '0.0';

                return (
                  <div
                    key={sectorName}
                    className="space-y-3 animate-fade-slide-up"
                    style={{ animationDelay: `${400 + sectorIdx * 80}ms` }}
                  >
                    <div className="flex items-center justify-between border-b border-white/10 pb-2">
                      <h3 className="font-semibold text-dashboard-text">{sectorName}</h3>
                      <div className="flex items-center gap-3">
                        <span className="text-sm font-medium font-mono text-dashboard-text">
                          {sectorPercentage}%
                        </span>
                        <span className="text-sm text-dashboard-text-muted">
                          {pluralAssets(sectorPositions.length)}
                        </span>
                      </div>
                    </div>
                    <div className="space-y-3">
                      {sectorPositions.map((position) => {
                        const colorIdx = (holdingColorIndex.get(position.ticker) ?? 0) % DONUT_COLORS.length;
                        const pnlPositive = (position.pnl ?? 0) >= 0;
                        return (
                          <div
                            key={position.id}
                            className="flex items-center justify-between p-3 bg-white/[0.03] rounded-lg hover:bg-white/[0.07] transition-all duration-200"
                          >
                            <div className="flex items-center space-x-4">
                              <div
                                className="w-10 h-10 rounded-full flex items-center justify-center shrink-0"
                                style={{ backgroundColor: `${DONUT_COLORS[colorIdx]}20` }}
                              >
                                <span className="font-bold text-sm" style={{ color: DONUT_COLORS[colorIdx] }}>
                                  {position.ticker.slice(0, 2)}
                                </span>
                              </div>
                              <div>
                                <div className="font-semibold text-dashboard-text">{position.ticker}</div>
                                <div className="text-sm text-dashboard-text-muted">{position.securityName}</div>
                                <div className="text-xs text-dashboard-text-muted font-mono">
                                  {position.quantity} × {formatCurrency(position.averagePrice)}
                                  {position.currentPrice !== null && (
                                    <span className="ml-2 text-dashboard-text-muted">
                                      Тек. цена: {formatCurrency(position.currentPrice)}
                                    </span>
                                  )}
                                  {position.currentPrice === null && (
                                    <span className="ml-2">Тек. цена: —</span>
                                  )}
                                </div>
                              </div>
                            </div>
                            <div className="text-right">
                              <div className="font-semibold text-dashboard-text font-mono">
                                {formatCurrency(position.totalCost)}
                              </div>
                              {position.pnl !== null && (
                                <div className={`text-xs font-mono ${pnlPositive ? 'text-emerald-400' : 'text-red-400'}`}>
                                  {pnlPositive ? '+' : ''}{formatCurrency(position.pnl)}
                                </div>
                              )}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {positions.length > 0 && <SecurityPriceChart positions={positions} />}

          {/* Recent transactions (top 5) */}
          {transactions.length > 0 && (
            <div className="glass-card p-5 animate-fade-slide-up" style={{ animationDelay: '480ms' }}>
              <h3 className="text-sm font-semibold text-dashboard-text mb-4">Последние сделки</h3>
              <div className="space-y-2">
                {transactions.slice(0, 5).map((tx) => (
                  <div
                    key={tx.id}
                    className="flex items-center justify-between p-3 bg-white/[0.03] rounded-lg hover:bg-white/[0.07] transition-all duration-200"
                  >
                    <div className="flex items-center gap-3">
                      <span
                        className={`text-xs font-bold px-2 py-0.5 rounded font-mono ${
                          tx.type === 'BUY'
                            ? 'bg-emerald-500/10 text-emerald-400'
                            : 'bg-red-500/10 text-red-400'
                        }`}
                      >
                        {tx.type}
                      </span>
                      <div>
                        <span className="font-semibold text-dashboard-text text-sm">{tx.ticker}</span>
                        <span className="text-dashboard-text-muted text-xs ml-2 font-mono">
                          {tx.quantity} × {formatCurrency(tx.price)}
                        </span>
                      </div>
                    </div>
                    <div className="flex items-center gap-3">
                      <span className="text-xs text-dashboard-text-muted">
                        {new Date(tx.executedAt).toLocaleDateString('ru-RU')}
                      </span>
                      <button
                        onClick={() => handleDeleteTransaction(tx.id)}
                        className="text-dashboard-text-muted hover:text-red-400 transition-colors p-1 rounded"
                        aria-label="Удалить сделку"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Sector allocation */}
        <div className="glass-card p-5 animate-fade-slide-up" style={{ animationDelay: '380ms' }}>
          <h3 className="text-sm font-semibold text-dashboard-text mb-4">Распределение по секторам</h3>
          <div className="space-y-4">
            {sectors.map((sector, index) => (
              <div key={sector.name} className="space-y-2">
                <div className="flex justify-between items-center">
                  <div className="flex items-center space-x-3">
                    <div className="w-3 h-3 rounded-full" style={{ backgroundColor: sector.color }} />
                    <span className="font-medium text-sm text-dashboard-text">{sector.name}</span>
                  </div>
                  <div className="text-right">
                    <div className="font-semibold text-sm text-dashboard-text font-mono">
                      {sector.percentage}%
                    </div>
                    <div className="text-xs text-dashboard-text-muted font-mono">
                      {formatCurrency(sector.value)}
                    </div>
                  </div>
                </div>
                <div className="w-full bg-white/5 rounded-full h-2">
                  <div
                    className="h-2 rounded-full animate-progress-grow"
                    style={{
                      width: `${sector.percentage}%`,
                      backgroundColor: sector.color,
                      animationDelay: `${500 + index * 80}ms`,
                    }}
                  />
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};

export default Investments;

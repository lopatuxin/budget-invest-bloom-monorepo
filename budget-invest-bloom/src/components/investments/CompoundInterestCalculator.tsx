import { useState } from 'react';
import { TrendingUp, Calculator, Percent, CalendarDays, Loader2 } from 'lucide-react';
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
} from 'recharts';
import { useProjection } from '@/hooks/useProjection';
import type { ProjectionPoint } from '@/types/investment';

const formatCurrency = (value: number) => value.toLocaleString('ru-RU') + ' ₽';

const Skeleton = ({ className = '' }: { className?: string }) => (
  <div className={`animate-pulse bg-white/10 rounded-lg ${className}`} />
);

const formatDate = (dateStr: string) =>
  new Date(dateStr).toLocaleDateString('ru-RU', { month: 'short', year: '2-digit' });

interface TooltipPayload {
  value: number;
  payload: ProjectionPoint;
}

const CustomTooltip = ({
  active,
  payload,
  label,
}: {
  active?: boolean;
  payload?: TooltipPayload[];
  label?: string;
}) => {
  if (!active || !payload?.length) return null;
  const p = payload[0].payload;
  return (
    <div className="bg-[#1a1f2e] border border-white/10 rounded-xl px-4 py-3 text-sm shadow-lg space-y-1">
      <p className="text-white/60 mb-2">{label}</p>
      <p className="text-white font-semibold font-mono">Стоимость: {formatCurrency(p.value)}</p>
      {p.deposit > 0 && (
        <p className="text-emerald-400/80 font-mono text-xs">Пополнение: {formatCurrency(p.deposit)}</p>
      )}
      {p.withdrawal > 0 && (
        <p className="text-red-400/80 font-mono text-xs">Изъятие: {formatCurrency(p.withdrawal)}</p>
      )}
    </div>
  );
};

const CompoundInterestCalculator = () => {
  const projection = useProjection();

  const [horizonMonths, setHorizonMonths] = useState(120);
  const [monthlyDeposit, setMonthlyDeposit] = useState(0);
  const [withdrawalRatePercent, setWithdrawalRatePercent] = useState(0);
  const handleCalculate = () => {
    projection.mutate({
      horizonMonths,
      monthlyDeposit,
      withdrawalRatePerYear: withdrawalRatePercent / 100,
      overrides: {},
    });
  };

  const result = projection.data?.body;
  const lastPoint = result?.series?.[result.series.length - 1];
  const horizonYears = Math.round(horizonMonths / 12);

  return (
    <div className="space-y-5">
      {/* Form */}
      <div className="glass-card p-5">
        <h3 className="text-sm font-semibold text-dashboard-text mb-5">Параметры прогноза</h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4">

          {/* Horizon */}
          <div className="space-y-1.5">
            <label className="flex items-center gap-1.5 text-[11px] font-semibold tracking-widest text-dashboard-text-muted uppercase">
              <CalendarDays className="w-3.5 h-3.5" />
              Горизонт (мес)
            </label>
            <input
              type="number"
              min={1}
              max={360}
              value={horizonMonths}
              onChange={e => setHorizonMonths(Math.max(1, Math.min(360, Number(e.target.value))))}
              className="w-full bg-white/5 border border-white/10 text-dashboard-text text-sm rounded-xl px-3 py-2.5 outline-none focus:border-blue-500/50 transition-colors font-mono"
            />
            <p className="text-[10px] text-dashboard-text-muted">
              ≈ {Math.round(horizonMonths / 12)} {horizonMonths / 12 >= 5 ? 'лет' : horizonMonths / 12 >= 2 ? 'года' : 'год'}
            </p>
          </div>

          {/* Monthly deposit */}
          <div className="space-y-1.5">
            <label className="flex items-center gap-1.5 text-[11px] font-semibold tracking-widest text-dashboard-text-muted uppercase">
              <TrendingUp className="w-3.5 h-3.5" />
              Пополнение (₽/мес)
            </label>
            <input
              type="number"
              min={0}
              value={monthlyDeposit}
              onChange={e => setMonthlyDeposit(Math.max(0, Number(e.target.value)))}
              className="w-full bg-white/5 border border-white/10 text-dashboard-text text-sm rounded-xl px-3 py-2.5 outline-none focus:border-blue-500/50 transition-colors font-mono"
            />
          </div>

          {/* Withdrawal rate */}
          <div className="space-y-1.5">
            <label className="flex items-center gap-1.5 text-[11px] font-semibold tracking-widests text-dashboard-text-muted uppercase">
              <Percent className="w-3.5 h-3.5" />
              Ставка изъятия (% / год)
            </label>
            <input
              type="number"
              min={0}
              max={100}
              step={0.1}
              value={withdrawalRatePercent}
              onChange={e => setWithdrawalRatePercent(Math.max(0, Math.min(100, Number(e.target.value))))}
              className="w-full bg-white/5 border border-white/10 text-dashboard-text text-sm rounded-xl px-3 py-2.5 outline-none focus:border-blue-500/50 transition-colors font-mono"
            />
          </div>

        </div>

        <div className="mt-5 flex justify-end">
          <button
            onClick={handleCalculate}
            disabled={projection.isPending}
            className="flex items-center gap-2 px-5 py-2.5 bg-blue-500/20 border border-blue-500/30 text-blue-400 hover:bg-blue-500/30 hover:border-blue-500/50 disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-200 rounded-xl text-sm font-semibold"
          >
            {projection.isPending ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" />
                Расчёт...
              </>
            ) : (
              <>
                <Calculator className="w-4 h-4" />
                Рассчитать
              </>
            )}
          </button>
        </div>
      </div>

      {/* Error state */}
      {projection.isError && (
        <div className="glass-card p-4 border-l-4 border-red-500">
          <p className="text-red-400 text-sm">
            {projection.error instanceof Error ? projection.error.message : 'Ошибка при расчёте прогноза'}
          </p>
        </div>
      )}

      {/* Loading skeleton for results */}
      {projection.isPending && (
        <div className="space-y-5">
          <div className="grid grid-cols-2 xl:grid-cols-4 gap-5">
            {Array.from({ length: 4 }).map((_, i) => (
              <Skeleton key={i} className="h-28" />
            ))}
          </div>
          <Skeleton className="h-72" />
        </div>
      )}

      {/* Results */}
      {result && !projection.isPending && (
        <div className="space-y-5">

          {/* Pending tickers banner */}
          {result.pendingHistoryTickers.length > 0 && (
            <div className="px-4 py-3 bg-yellow-500/10 border border-yellow-500/20 rounded-xl text-yellow-400 text-sm">
              Данные истории по тикерам <span className="font-mono font-semibold">{result.pendingHistoryTickers.join(', ')}</span> ещё загружаются. Прогноз приблизительный.
            </div>
          )}

          {/* No positions message */}
          {result.startValue === 0 && (
            <div className="glass-card p-8 text-center">
              <TrendingUp className="w-12 h-12 text-dashboard-text-muted mx-auto mb-3" />
              <p className="text-dashboard-text font-semibold mb-1">Портфель пуст</p>
              <p className="text-dashboard-text-muted text-sm">
                Добавьте активы в портфель для расчёта прогноза
              </p>
            </div>
          )}

          {/* KPI cards */}
          {result.startValue > 0 && (
            <>
              <div className="grid grid-cols-2 xl:grid-cols-4 gap-5">
                <div className="glass-card p-5 flex items-start justify-between" style={{ borderLeft: '3px solid #3B82F6' }}>
                  <div className="space-y-2">
                    <p className="text-[11px] font-semibold tracking-widest text-dashboard-text-muted">СТОИМОСТЬ СЕЙЧАС</p>
                    <p className="text-xl font-bold text-dashboard-text font-mono">
                      {formatCurrency(result.startValue)}
                    </p>
                  </div>
                  <div
                    className="w-11 h-11 rounded-xl flex items-center justify-center shrink-0"
                    style={{ backgroundColor: '#3B82F620', boxShadow: '0 0 20px rgba(59,130,246,0.3)' }}
                  >
                    <TrendingUp className="w-5 h-5 text-blue-400" />
                  </div>
                </div>

                <div className="glass-card p-5 flex items-start justify-between" style={{ borderLeft: '3px solid #10B981' }}>
                  <div className="space-y-2">
                    <p className="text-[11px] font-semibold tracking-widest text-dashboard-text-muted">
                      ЧЕРЕЗ {horizonYears} {horizonYears >= 5 ? 'ЛЕТ' : horizonYears >= 2 ? 'ГОДА' : 'ГОД'}
                    </p>
                    <p className="text-xl font-bold text-dashboard-text font-mono">
                      {lastPoint ? formatCurrency(lastPoint.value) : '—'}
                    </p>
                  </div>
                  <div
                    className="w-11 h-11 rounded-xl flex items-center justify-center shrink-0"
                    style={{ backgroundColor: '#10B98120', boxShadow: '0 0 20px rgba(16,185,129,0.3)' }}
                  >
                    <Calculator className="w-5 h-5 text-emerald-400" />
                  </div>
                </div>

                <div className="glass-card p-5 flex items-start justify-between" style={{ borderLeft: '3px solid #8B5CF6' }}>
                  <div className="space-y-2">
                    <p className="text-[11px] font-semibold tracking-widest text-dashboard-text-muted">ДОХОДНОСТЬ В ГОД</p>
                    <p className="text-xl font-bold text-dashboard-text font-mono">
                      {(result.portfolioWeightedAnnualReturn * 100).toFixed(2)}%
                    </p>
                  </div>
                  <div
                    className="w-11 h-11 rounded-xl flex items-center justify-center shrink-0"
                    style={{ backgroundColor: '#8B5CF620', boxShadow: '0 0 20px rgba(139,92,246,0.3)' }}
                  >
                    <Percent className="w-5 h-5 text-violet-400" />
                  </div>
                </div>

                <div className="glass-card p-5 flex items-start justify-between" style={{ borderLeft: '3px solid #F59E0B' }}>
                  <div className="space-y-2">
                    <p className="text-[11px] font-semibold tracking-widest text-dashboard-text-muted">ДОХОДНОСТЬ В МЕС</p>
                    <p className="text-xl font-bold text-dashboard-text font-mono">
                      {(result.monthlyReturn * 100).toFixed(2)}%
                    </p>
                  </div>
                  <div
                    className="w-11 h-11 rounded-xl flex items-center justify-center shrink-0"
                    style={{ backgroundColor: '#F59E0B20', boxShadow: '0 0 20px rgba(245,158,11,0.3)' }}
                  >
                    <Percent className="w-5 h-5 text-amber-400" />
                  </div>
                </div>
              </div>

              {/* Projection chart */}
              {result.series.length > 0 && (
                <div className="bg-white/5 rounded-2xl p-6">
                  <h3 className="text-sm font-semibold text-white mb-5">Прогноз роста портфеля</h3>
                  <ResponsiveContainer width="100%" height={280}>
                    <AreaChart data={result.series} margin={{ top: 4, right: 4, left: 0, bottom: 0 }}>
                      <defs>
                        <linearGradient id="projectionGrad" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="5%" stopColor="#10B981" stopOpacity={0.25} />
                          <stop offset="95%" stopColor="#10B981" stopOpacity={0} />
                        </linearGradient>
                      </defs>
                      <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.06)" />
                      <XAxis
                        dataKey="date"
                        tickFormatter={formatDate}
                        tick={{ fill: 'rgba(255,255,255,0.4)', fontSize: 11 }}
                        axisLine={false}
                        tickLine={false}
                        interval="preserveStartEnd"
                      />
                      <YAxis
                        tickFormatter={(v: number) => v.toLocaleString('ru-RU')}
                        tick={{ fill: 'rgba(255,255,255,0.4)', fontSize: 11 }}
                        axisLine={false}
                        tickLine={false}
                        width={80}
                      />
                      <Tooltip content={<CustomTooltip />} />
                      <Area
                        type="monotone"
                        dataKey="value"
                        stroke="#10B981"
                        strokeWidth={2}
                        fill="url(#projectionGrad)"
                        dot={false}
                        activeDot={{ r: 4, fill: '#10B981', stroke: 'transparent' }}
                      />
                    </AreaChart>
                  </ResponsiveContainer>
                </div>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
};

export default CompoundInterestCalculator;

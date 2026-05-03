import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, LineChart as LineChartIcon } from 'lucide-react';
import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
} from 'recharts';
import { usePortfolioValueHistory } from '@/hooks/usePortfolioValueHistory';
import { getPeriodDates } from '@/lib/periodDates';
import type { Period } from '@/lib/periodDates';
import type { PortfolioValuePoint } from '@/types/investment';
import CompoundInterestCalculator from '@/components/investments/CompoundInterestCalculator';

const formatCurrency = (value: number) => value.toLocaleString('ru-RU') + ' ₽';

const Skeleton = ({ className = '' }: { className?: string }) => (
  <div className={`animate-pulse bg-white/10 rounded-lg ${className}`} />
);

const PERIODS: Period[] = ['1M', '3M', '1Y', 'MAX'];

interface ChartTooltipPayload {
  value: number;
  payload: PortfolioValuePoint;
}

const CustomTooltip = ({
  active,
  payload,
  label,
}: {
  active?: boolean;
  payload?: ChartTooltipPayload[];
  label?: string;
}) => {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-[#1a1f2e] border border-white/10 rounded-xl px-4 py-3 text-sm shadow-lg">
      <p className="text-white/60 mb-1">{label}</p>
      <p className="text-white font-semibold font-mono">{formatCurrency(payload[0].value)}</p>
    </div>
  );
};

const PortfolioAnalytics = () => {
  const navigate = useNavigate();
  const [period, setPeriod] = useState<Period>('1Y');

  const { from, to } = getPeriodDates(period);

  const { data: historyData, isLoading } = usePortfolioValueHistory(from, to);

  const series = historyData?.body?.series ?? [];
  const historyPending = historyData?.body?.historyPending ?? false;

  // Compute KPI from series
  const firstValue = series.length > 0 ? series[0].value : null;
  const lastValue = series.length > 0 ? series[series.length - 1].value : null;
  const absoluteChange = firstValue !== null && lastValue !== null ? lastValue - firstValue : null;
  const percentChange =
    firstValue !== null && firstValue !== 0 && absoluteChange !== null
      ? (absoluteChange / firstValue) * 100
      : null;
  const changePositive = (absoluteChange ?? 0) >= 0;

  return (
    <div className="space-y-6 pb-6">
      {/* Header */}
      <div className="animate-fade-slide-up">
        <button
          onClick={() => navigate(-1)}
          className="flex items-center gap-2 text-sm text-dashboard-text-muted hover:text-dashboard-text transition-colors mb-4"
        >
          <ArrowLeft className="w-4 h-4" />
          Назад
        </button>

        <div className="flex items-center gap-3">
          <div
            className="w-11 h-11 rounded-xl flex items-center justify-center shrink-0"
            style={{ backgroundColor: '#3B82F620', boxShadow: '0 0 20px rgba(59,130,246,0.3)' }}
          >
            <LineChartIcon className="w-5 h-5 text-blue-400" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-dashboard-text">Аналитика портфеля</h1>
            <p className="text-sm text-dashboard-text-muted">Динамика стоимости и прогноз роста</p>
          </div>
        </div>
      </div>

      {/* KPI cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-5 animate-fade-slide-up" style={{ animationDelay: '60ms' }}>
        {/* Current value */}
        <div className="glass-card p-5 flex items-start justify-between" style={{ borderLeft: '3px solid #3B82F6' }}>
          <div className="space-y-2">
            <p className="text-[11px] font-semibold tracking-widest text-dashboard-text-muted">СТОИМОСТЬ ПОРТФЕЛЯ</p>
            {isLoading ? (
              <Skeleton className="h-8 w-36" />
            ) : (
              <p className="text-2xl font-bold text-dashboard-text font-mono">
                {lastValue !== null ? formatCurrency(lastValue) : '—'}
              </p>
            )}
          </div>
          <div
            className="w-11 h-11 rounded-xl flex items-center justify-center shrink-0"
            style={{ backgroundColor: '#3B82F620', boxShadow: '0 0 20px rgba(59,130,246,0.3)' }}
          >
            <LineChartIcon className="w-5 h-5 text-blue-400" />
          </div>
        </div>

        {/* Period change */}
        <div
          className="glass-card p-5 flex items-start justify-between"
          style={{ borderLeft: `3px solid ${changePositive ? '#10B981' : '#EF4444'}` }}
        >
          <div className="space-y-2">
            <p className="text-[11px] font-semibold tracking-widest text-dashboard-text-muted">
              ИЗМЕНЕНИЕ ЗА {period}
            </p>
            {isLoading ? (
              <Skeleton className="h-8 w-36" />
            ) : absoluteChange !== null ? (
              <div>
                <p className={`text-2xl font-bold font-mono ${changePositive ? 'text-emerald-400' : 'text-red-400'}`}>
                  {changePositive ? '+' : ''}{formatCurrency(absoluteChange)}
                </p>
                {percentChange !== null && (
                  <p className={`text-sm font-mono ${changePositive ? 'text-emerald-400/70' : 'text-red-400/70'}`}>
                    {changePositive ? '+' : ''}{percentChange.toFixed(2)}%
                  </p>
                )}
              </div>
            ) : (
              <p className="text-2xl font-bold text-dashboard-text-muted font-mono">—</p>
            )}
          </div>
          <div
            className="w-11 h-11 rounded-xl flex items-center justify-center shrink-0"
            style={{
              backgroundColor: changePositive ? '#10B98120' : '#EF444420',
              boxShadow: changePositive ? '0 0 20px rgba(16,185,129,0.3)' : '0 0 20px rgba(239,68,68,0.3)',
            }}
          >
            <LineChartIcon className={`w-5 h-5 ${changePositive ? 'text-emerald-400' : 'text-red-400'}`} />
          </div>
        </div>
      </div>

      {/* Portfolio value chart */}
      <div className="glass-card p-5 animate-fade-slide-up" style={{ animationDelay: '120ms' }}>
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-semibold text-dashboard-text">Стоимость портфеля</h3>
          <div className="flex gap-1">
            {PERIODS.map((p) => (
              <button
                key={p}
                onClick={() => setPeriod(p)}
                className={`px-3 py-1 rounded-lg text-xs font-mono font-semibold transition-colors ${
                  period === p
                    ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                    : 'text-dashboard-text-muted hover:text-dashboard-text hover:bg-white/5'
                }`}
              >
                {p}
              </button>
            ))}
          </div>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center h-48 text-dashboard-text-muted text-sm">
            Загружается история...
          </div>
        ) : historyPending ? (
          <div className="flex items-center justify-center h-48 text-dashboard-text-muted text-sm">
            История загружается, попробуйте позже
          </div>
        ) : series.length === 0 ? (
          <div className="flex items-center justify-center h-48 text-dashboard-text-muted text-sm">
            Нет данных за выбранный период
          </div>
        ) : (
          <ResponsiveContainer width="100%" height={220}>
            <LineChart data={series} margin={{ top: 4, right: 4, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
              <XAxis
                dataKey="date"
                tick={{ fill: 'rgba(255,255,255,0.4)', fontSize: 11 }}
                tickLine={false}
                axisLine={false}
                tickFormatter={(v: string) =>
                  new Date(v).toLocaleDateString('ru-RU', { day: '2-digit', month: 'short' })
                }
                interval="preserveStartEnd"
              />
              <YAxis
                tick={{ fill: 'rgba(255,255,255,0.4)', fontSize: 11 }}
                tickLine={false}
                axisLine={false}
                tickFormatter={(v: number) => v.toLocaleString('ru-RU')}
                width={72}
              />
              <Tooltip content={<CustomTooltip />} />
              <Line
                type="monotone"
                dataKey="value"
                stroke="#3B82F6"
                strokeWidth={2}
                dot={false}
                activeDot={{ r: 4, fill: '#3B82F6' }}
              />
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>

      {/* Compound interest calculator section */}
      <div className="animate-fade-slide-up" style={{ animationDelay: '180ms' }}>
        <div className="flex items-center gap-2 mb-4">
          <h2 className="text-base font-semibold text-dashboard-text">Калькулятор сложного процента</h2>
        </div>
        <CompoundInterestCalculator />
      </div>
    </div>
  );
};

export default PortfolioAnalytics;

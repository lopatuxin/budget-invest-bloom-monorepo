import { useState } from 'react';
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
} from 'recharts';
import { usePortfolioValueHistory } from '@/hooks/usePortfolioValueHistory';

type Period = '1M' | '3M' | '6M' | '1Y' | '3Y';

const PERIODS: { key: Period; label: string }[] = [
  { key: '1M', label: '1М' },
  { key: '3M', label: '3М' },
  { key: '6M', label: '6М' },
  { key: '1Y', label: '1Г' },
  { key: '3Y', label: '3Г' },
];

function getPeriodDates(period: Period): { from: string; to: string } {
  const to = new Date();
  const from = new Date();
  if (period === '1M') from.setMonth(from.getMonth() - 1);
  else if (period === '3M') from.setMonth(from.getMonth() - 3);
  else if (period === '6M') from.setMonth(from.getMonth() - 6);
  else if (period === '1Y') from.setFullYear(from.getFullYear() - 1);
  else from.setFullYear(from.getFullYear() - 3);
  return {
    from: from.toISOString().slice(0, 10),
    to: to.toISOString().slice(0, 10),
  };
}

const Skeleton = ({ className = '' }: { className?: string }) => (
  <div className={`animate-pulse bg-white/10 rounded-lg ${className}`} />
);

const formatDate = (dateStr: string) =>
  new Date(dateStr).toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' });

const formatRub = (value: number) => value.toLocaleString('ru-RU') + ' ₽';

const CustomTooltip = ({ active, payload, label }: { active?: boolean; payload?: { value: number }[]; label?: string }) => {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-[#1a1f2e] border border-white/10 rounded-xl px-4 py-2 text-sm shadow-lg">
      <p className="text-white/60 mb-1">{label}</p>
      <p className="text-white font-semibold font-mono">{formatRub(payload[0].value)}</p>
    </div>
  );
};

const PortfolioValueChart = () => {
  const [period, setPeriod] = useState<Period>('1Y');
  const { from, to } = getPeriodDates(period);
  const { data, isLoading } = usePortfolioValueHistory(from, to);

  const points = data?.body?.series ?? [];
  const historyPending = data?.body?.historyPending ?? false;
  const pendingTickers = data?.body?.pendingTickers ?? [];

  return (
    <div className="bg-white/5 rounded-2xl p-6 animate-fade-slide-up">
      <div className="flex items-center justify-between mb-5">
        <h3 className="text-sm font-semibold text-white">Динамика портфеля</h3>
        <div className="flex gap-1">
          {PERIODS.map(p => (
            <button
              key={p.key}
              onClick={() => setPeriod(p.key)}
              className={`px-2.5 py-1 rounded-full text-xs font-medium transition-colors ${
                period === p.key
                  ? 'bg-white/15 text-white'
                  : 'text-white/60 hover:text-white'
              }`}
            >
              {p.label}
            </button>
          ))}
        </div>
      </div>

      {historyPending && pendingTickers.length > 0 && (
        <div className="mb-3 px-3 py-2 bg-yellow-500/10 border border-yellow-500/20 rounded-xl text-yellow-400 text-xs">
          История загружается по тикерам: {pendingTickers.join(', ')}. Данные могут быть неполными.
        </div>
      )}

      {isLoading ? (
        <Skeleton className="h-56 w-full" />
      ) : points.length === 0 ? (
        <div className="h-56 flex items-center justify-center text-white/40 text-sm">
          Нет данных за выбранный период
        </div>
      ) : (
        <ResponsiveContainer width="100%" height={224}>
          <AreaChart data={points} margin={{ top: 4, right: 4, left: 0, bottom: 0 }}>
            <defs>
              <linearGradient id="portfolioGrad" x1="0" y1="0" x2="0" y2="1">
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
              width={70}
            />
            <Tooltip content={<CustomTooltip />} />
            <Area
              type="monotone"
              dataKey="value"
              stroke="#10B981"
              strokeWidth={2}
              fill="url(#portfolioGrad)"
              dot={false}
              activeDot={{ r: 4, fill: '#10B981', stroke: 'transparent' }}
            />
          </AreaChart>
        </ResponsiveContainer>
      )}
    </div>
  );
};

export default PortfolioValueChart;

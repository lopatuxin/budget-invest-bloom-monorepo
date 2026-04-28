import { useState, useEffect } from 'react';
import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
} from 'recharts';
import { useSecurityPriceHistory } from '@/hooks/useSecurityPriceHistory';
import type { PositionResponse, PricePoint } from '@/types/investment';

type Period = '1M' | '3M' | '6M' | '1Y';

const PERIODS: { key: Period; label: string }[] = [
  { key: '1M', label: '1М' },
  { key: '3M', label: '3М' },
  { key: '6M', label: '6М' },
  { key: '1Y', label: '1Г' },
];

function getPeriodDates(period: Period): { from: string; to: string } {
  const to = new Date();
  const from = new Date();
  if (period === '1M') from.setMonth(from.getMonth() - 1);
  else if (period === '3M') from.setMonth(from.getMonth() - 3);
  else if (period === '6M') from.setMonth(from.getMonth() - 6);
  else from.setFullYear(from.getFullYear() - 1);
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

interface TooltipPayloadEntry {
  value: number;
  payload: PricePoint;
}

const CustomTooltip = ({
  active,
  payload,
  label,
}: {
  active?: boolean;
  payload?: TooltipPayloadEntry[];
  label?: string;
}) => {
  if (!active || !payload?.length) return null;
  const p = payload[0].payload;
  return (
    <div className="bg-[#1a1f2e] border border-white/10 rounded-xl px-4 py-3 text-sm shadow-lg space-y-1">
      <p className="text-white/60 mb-2">{label}</p>
      <p className="text-white font-semibold font-mono">Закр: {formatRub(p.close)}</p>
      <p className="text-white/60 font-mono">Откр: {formatRub(p.open)}</p>
      <p className="text-white/60 font-mono">Макс: {formatRub(p.high)}</p>
      <p className="text-white/60 font-mono">Мин: {formatRub(p.low)}</p>
    </div>
  );
};

interface Props {
  positions: PositionResponse[];
}

const SecurityPriceChart = ({ positions }: Props) => {
  const [period, setPeriod] = useState<Period>('1M');
  const [selectedTicker, setSelectedTicker] = useState<string | null>(
    positions.length > 0 ? positions[0].ticker : null
  );

  useEffect(() => {
    if (!selectedTicker && positions.length > 0) {
      setSelectedTicker(positions[0].ticker);
    }
  }, [positions, selectedTicker]);

  const { from, to } = getPeriodDates(period);
  const { data, isLoading } = useSecurityPriceHistory(selectedTicker, from, to);

  const points = data?.body?.series ?? [];
  const historyPending = data?.body?.historyPending ?? false;
  const pendingTickers = data?.body?.pendingTickers ?? [];

  return (
    <div className="bg-white/5 rounded-2xl p-6 animate-fade-slide-up">
      <div className="flex flex-col gap-3 mb-5 sm:flex-row sm:items-center sm:justify-between">
        <h3 className="text-sm font-semibold text-white">Цена бумаги</h3>
        <div className="flex flex-wrap items-center gap-3">
          <select
            value={selectedTicker ?? ''}
            onChange={e => setSelectedTicker(e.target.value || null)}
            className="bg-white/10 border border-white/10 text-white text-xs rounded-lg px-3 py-1.5 outline-none focus:border-white/25 transition-colors cursor-pointer"
          >
            {positions.map(pos => (
              <option key={pos.ticker} value={pos.ticker} className="bg-[#1a1f2e]">
                {pos.ticker} — {pos.securityName}
              </option>
            ))}
          </select>
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
          <LineChart data={points} margin={{ top: 4, right: 4, left: 0, bottom: 0 }}>
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
            <Line
              type="monotone"
              dataKey="close"
              stroke="#3B82F6"
              strokeWidth={2}
              dot={false}
              activeDot={{ r: 4, fill: '#3B82F6', stroke: 'transparent' }}
            />
          </LineChart>
        </ResponsiveContainer>
      )}
    </div>
  );
};

export default SecurityPriceChart;

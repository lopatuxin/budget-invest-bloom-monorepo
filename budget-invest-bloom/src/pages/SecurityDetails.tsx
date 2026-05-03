import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
} from 'recharts';
import { SecurityLogo } from '@/components/SecurityLogo';
import { usePositionByTicker } from '@/hooks/usePositionByTicker';
import { useSecurityPriceHistory } from '@/hooks/useSecurityPriceHistory';
import { useTransactions } from '@/hooks/useTransactions';
import { useSecurityDividendsHistory } from '@/hooks/useSecurityDividendsHistory';
import type { PricePoint } from '@/types/investment';

type Period = '1M' | '3M' | '1Y' | 'MAX';

const formatCurrency = (value: number) => value.toLocaleString('ru-RU') + ' ₽';

const Skeleton = ({ className = '' }: { className?: string }) => (
  <div className={`animate-pulse bg-white/10 rounded-lg ${className}`} />
);

function getPeriodDates(period: Period): { from: string; to: string } {
  const today = new Date();
  const to = today.toISOString().slice(0, 10);

  const from = new Date(today);
  if (period === '1M') {
    from.setMonth(from.getMonth() - 1);
  } else if (period === '3M') {
    from.setMonth(from.getMonth() - 3);
  } else if (period === '1Y') {
    from.setFullYear(from.getFullYear() - 1);
  } else {
    return { from: '2010-01-01', to };
  }

  return { from: from.toISOString().slice(0, 10), to };
}

const PERIODS: Period[] = ['1M', '3M', '1Y', 'MAX'];

interface ChartTooltipPayload {
  value: number;
  payload: PricePoint;
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

const SecurityDetails = () => {
  const { ticker } = useParams<{ ticker: string }>();
  const navigate = useNavigate();
  const [period, setPeriod] = useState<Period>('1Y');

  const { from, to } = getPeriodDates(period);

  const { data: positionData, isLoading: positionLoading } = usePositionByTicker(ticker ?? null);
  const { data: priceHistoryData, isLoading: priceLoading } = useSecurityPriceHistory(ticker ?? null, from, to);
  const { data: transactionsData, isLoading: transactionsLoading } = useTransactions(ticker);
  const { data: dividendsData, isLoading: dividendsLoading } = useSecurityDividendsHistory(ticker ?? null);

  const position = positionData?.body;
  const series = priceHistoryData?.body?.series ?? [];
  const historyPending = priceHistoryData?.body?.historyPending ?? false;
  const transactions = transactionsData?.body ?? [];
  const dividends = dividendsData?.body ?? [];

  const pnlPositive = (position?.pnl ?? 0) >= 0;

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

        {positionLoading ? (
          <div className="flex items-center gap-4">
            <Skeleton className="w-14 h-14 rounded-full" />
            <div className="space-y-2 flex-1">
              <Skeleton className="h-7 w-32" />
              <Skeleton className="h-4 w-48" />
            </div>
            <div className="space-y-2 text-right">
              <Skeleton className="h-6 w-28 ml-auto" />
              <Skeleton className="h-4 w-20 ml-auto" />
            </div>
          </div>
        ) : position ? (
          <div className="flex items-center gap-4">
            <SecurityLogo ticker={position.ticker} size={56} securityType={position.securityType} />
            <div className="flex-1 min-w-0">
              <div className="text-2xl font-bold text-dashboard-text font-mono">{position.ticker}</div>
              <div className="text-sm text-dashboard-text-muted truncate">{position.securityName}</div>
            </div>
            <div className="text-right shrink-0">
              {position.currentPrice !== null ? (
                <div className="text-lg font-semibold text-dashboard-text font-mono">
                  {formatCurrency(position.currentPrice)}
                </div>
              ) : (
                <div className="text-lg font-semibold text-dashboard-text-muted">—</div>
              )}
              {position.pnl !== null && (
                <div className={`text-sm font-mono ${pnlPositive ? 'text-emerald-400' : 'text-red-400'}`}>
                  {pnlPositive ? '+' : ''}{formatCurrency(position.pnl)}
                </div>
              )}
            </div>
          </div>
        ) : (
          <div className="flex items-center gap-4">
            <SecurityLogo ticker={ticker ?? ''} size={56} />
            <div className="text-2xl font-bold text-dashboard-text font-mono">{ticker}</div>
          </div>
        )}
      </div>

      {/* Price chart */}
      <div className="glass-card p-5 animate-fade-slide-up" style={{ animationDelay: '60ms' }}>
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-semibold text-dashboard-text">График цены</h3>
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

        {priceLoading ? (
          <div className="flex items-center justify-center h-48 text-dashboard-text-muted text-sm">
            Загружается история...
          </div>
        ) : historyPending ? (
          <div className="flex items-center justify-center h-48 text-dashboard-text-muted text-sm">
            История загружается, попробуйте позже
          </div>
        ) : series.length === 0 ? (
          <div className="flex items-center justify-center h-48 text-dashboard-text-muted text-sm">
            Нет данных
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
                width={60}
              />
              <Tooltip content={<CustomTooltip />} />
              <Line
                type="monotone"
                dataKey="close"
                stroke="#10B981"
                strokeWidth={2}
                dot={false}
                activeDot={{ r: 4, fill: '#10B981' }}
              />
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>

      {/* My transactions */}
      <div className="glass-card p-5 animate-fade-slide-up" style={{ animationDelay: '120ms' }}>
        <h3 className="text-sm font-semibold text-dashboard-text mb-4">Мои операции</h3>
        {transactionsLoading ? (
          <div className="space-y-2">
            {[...Array(3)].map((_, i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        ) : transactions.length === 0 ? (
          <p className="text-dashboard-text-muted text-sm">Нет сделок</p>
        ) : (
          <div className="space-y-2">
            {transactions.map((tx) => (
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
                  <span className="text-dashboard-text-muted text-xs font-mono">
                    {tx.quantity} × {formatCurrency(tx.price)}
                  </span>
                </div>
                <div className="flex items-center gap-4">
                  <span className="font-semibold text-dashboard-text font-mono text-sm">
                    {formatCurrency(tx.quantity * tx.price)}
                  </span>
                  <span className="text-xs text-dashboard-text-muted">
                    {new Date(tx.executedAt).toLocaleDateString('ru-RU')}
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Dividends history */}
      <div className="glass-card p-5 animate-fade-slide-up" style={{ animationDelay: '180ms' }}>
        <h3 className="text-sm font-semibold text-dashboard-text mb-4">История дивидендов</h3>
        {dividendsLoading ? (
          <div className="space-y-2">
            {[...Array(3)].map((_, i) => (
              <Skeleton key={i} className="h-10" />
            ))}
          </div>
        ) : dividends.length === 0 ? (
          <p className="text-dashboard-text-muted text-sm">Нет выплаченных дивидендов</p>
        ) : (
          <div className="space-y-2">
            {dividends.map((div, i) => (
              <div
                key={i}
                className="flex items-center justify-between p-3 bg-white/[0.03] rounded-lg"
              >
                <span className="text-sm text-dashboard-text-muted">
                  {new Date(div.paymentDate).toLocaleDateString('ru-RU')}
                </span>
                <span className="text-sm font-mono text-dashboard-text">
                  {div.amountPerShare.toLocaleString('ru-RU')} {div.currency} на акцию
                </span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default SecurityDetails;

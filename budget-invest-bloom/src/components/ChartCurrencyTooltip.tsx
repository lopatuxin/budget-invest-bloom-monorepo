import { formatCurrency } from '@/lib/dateOptions';

interface TooltipEntry {
  value: number;
  // recharts passes the full data record in payload; keep it generic
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  payload?: any;
}

interface ChartCurrencyTooltipProps {
  active?: boolean;
  payload?: TooltipEntry[];
  label?: string;
}

/** Shared recharts Tooltip that shows a date label and a currency value. */
export const ChartCurrencyTooltip = ({
  active,
  payload,
  label,
}: ChartCurrencyTooltipProps) => {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-[#1a1f2e] border border-white/10 rounded-xl px-4 py-3 text-sm shadow-lg">
      <p className="text-white/60 mb-1">{label}</p>
      <p className="text-white font-semibold font-mono">{formatCurrency(payload[0].value)}</p>
    </div>
  );
};

import { ArrowDownRight, ArrowUpRight } from 'lucide-react';
import { formatSignedPercent } from '@/lib/dateOptions';
import { signClass } from '@/pages/investments/investmentsFormat';

const BADGE_STYLES: Record<'good' | 'bad' | 'neutral', string> = {
  good: 'bg-app-good-soft text-app-good',
  bad: 'bg-app-bad-soft text-app-bad',
  neutral: 'bg-app-neutral-soft text-app-neutral',
};

// Same visual as PositionCard's private PnlBadge — duplicated because
// pages/investments/* is out of scope here beyond the dialog/forecast-link swap,
// so that component can't be exported and reused as-is.
export function SecurityPnlBadge({ percent }: { percent: number }) {
  const Arrow = percent >= 0 ? ArrowUpRight : ArrowDownRight;
  return (
    <span
      className={`inline-flex items-center gap-0.5 h-5 px-1.5 rounded-full font-mono text-xs font-semibold whitespace-nowrap ${BADGE_STYLES[signClass(percent)]}`}
    >
      <Arrow aria-hidden="true" className="w-3 h-3" />
      {formatSignedPercent(percent)}
    </span>
  );
}

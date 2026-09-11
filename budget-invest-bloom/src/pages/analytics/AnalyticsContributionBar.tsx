import { formatContributionPoints } from '@/pages/analytics/analyticsFormat';

interface AnalyticsContributionBarProps {
  points: number;
  // Largest |contributionPoints| among the categories shown, for scaling the bar length.
  maxAbsPoints: number;
}

// Diverging bar with the zero axis in the middle: positive contribution grows right
// (warn color), negative grows left (good color) — see analytics-redesign plan p.17.
export function AnalyticsContributionBar({ points, maxAbsPoints }: AnalyticsContributionBarProps) {
  const widthPercent = maxAbsPoints > 0 ? (Math.abs(points) / maxAbsPoints) * 50 : 0;

  return (
    <span className="inline-flex items-center gap-2 justify-end">
      <span className="relative w-[110px] h-3 shrink-0">
        <span className="absolute left-1/2 top-0 bottom-0 w-px bg-app-border-strong" />
        {points >= 0 ? (
          <span className="absolute left-1/2 top-0.5 h-2 rounded-r-[3px] bg-app-warn" style={{ width: `${widthPercent}%` }} />
        ) : (
          <span className="absolute right-1/2 top-0.5 h-2 rounded-l-[3px] bg-app-good" style={{ width: `${widthPercent}%` }} />
        )}
      </span>
      <span className="font-mono text-xs whitespace-nowrap text-app-text">{formatContributionPoints(points)}</span>
    </span>
  );
}

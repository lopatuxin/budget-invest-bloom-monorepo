import { PORTFOLIO_CHART_COLOR } from '@/pages/investments/investmentsFormat';

interface SecurityLastPointDotProps {
  lastIndex: number;
  // Injected by recharts, which clones the Area's `dot` element once per series point
  cx?: number;
  cy?: number;
  index?: number;
}

// Same look as PortfolioValueChart's private last-point renderer, which isn't exported:
// only the newest price gets a white-ringed dot.
export function SecurityLastPointDot({ lastIndex, cx, cy, index }: SecurityLastPointDotProps) {
  if (index !== lastIndex || cx == null || cy == null) return null;
  return (
    <g>
      <circle cx={cx} cy={cy} r={6} fill="#FFFFFF" />
      <circle cx={cx} cy={cy} r={4} fill={PORTFOLIO_CHART_COLOR} />
    </g>
  );
}

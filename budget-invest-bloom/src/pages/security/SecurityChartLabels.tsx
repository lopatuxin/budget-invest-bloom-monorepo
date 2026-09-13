import { CHART_TEXT_COLOR } from '@/pages/investments/investmentsFormat';
import { averagePriceLabel, layoutChartLabels, type ChartLabel, type MarkerGroup } from '@/pages/security/securityChartMarkers';
import { SECURITY_BUY_COLOR, SECURITY_SELL_COLOR } from '@/pages/security/securityFormat';

const LABEL_COLOR: Record<ChartLabel['kind'], string> = {
  BUY: SECURITY_BUY_COLOR,
  SELL: SECURITY_SELL_COLOR,
  AVERAGE: CHART_TEXT_COLOR,
};

interface RechartsAxis {
  scale: (value: number) => number;
}

interface SecurityChartLabelsProps {
  markerGroups: MarkerGroup[];
  closes: number[];
  averagePrice: number | null;
  // Injected by recharts: <Customized component={<SecurityChartLabels … />} /> clones this
  // element with the chart state, which is the only place the plot box and axis scales exist.
  offset?: { left: number; top: number; width: number; height: number };
  xAxisMap?: Record<string, RechartsAxis>;
  yAxisMap?: Record<string, RechartsAxis>;
}

// All captions of the price chart in one layer: ReferenceDot/ReferenceLine labels are placed
// independently and collided at the right edge, so positions are computed here together.
export function SecurityChartLabels({ markerGroups, closes, averagePrice, offset, xAxisMap, yAxisMap }: SecurityChartLabelsProps) {
  const xAxis = xAxisMap ? Object.values(xAxisMap)[0] : undefined;
  const yAxis = yAxisMap ? Object.values(yAxisMap)[0] : undefined;
  if (!offset || !xAxis || !yAxis || closes.length === 0) return null;

  const lastIndex = closes.length - 1;
  const labels = layoutChartLabels(
    { left: offset.left, top: offset.top, right: offset.left + offset.width, bottom: offset.top + offset.height },
    markerGroups.map((group) => ({ group, point: { x: xAxis.scale(group.index), y: yAxis.scale(closes[group.index]) } })),
    averagePrice != null ? { text: averagePriceLabel(averagePrice), y: yAxis.scale(averagePrice) } : null,
    { x: xAxis.scale(lastIndex), y: yAxis.scale(closes[lastIndex]) },
  );

  return (
    <g>
      {labels.map((label) => (
        // The white stroke painted under the glyphs keeps a caption readable where the price line crosses it
        <text
          key={label.key}
          x={label.x}
          y={label.y}
          textAnchor={label.anchor}
          fontSize={11}
          fontWeight={label.kind === 'AVERAGE' ? 500 : 400}
          fill={LABEL_COLOR[label.kind]}
          stroke="#FFFFFF"
          strokeWidth={3}
          strokeLinejoin="round"
          paintOrder="stroke"
        >
          {label.text}
        </text>
      ))}
    </g>
  );
}

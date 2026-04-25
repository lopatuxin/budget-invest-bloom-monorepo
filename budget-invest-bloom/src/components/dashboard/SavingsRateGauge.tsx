// Horizontal gauge component for savings rate KPI card
const RANGE_MAX = 50;
const RED_END = 10;
const ORANGE_END = 30;

// Section widths as percentages of total range (0–RANGE_MAX)
const RED_WIDTH = (RED_END / RANGE_MAX) * 100;       // 20%
const ORANGE_WIDTH = ((ORANGE_END - RED_END) / RANGE_MAX) * 100; // 40%
const GREEN_WIDTH = ((RANGE_MAX - ORANGE_END) / RANGE_MAX) * 100; // 40%

// Threshold label positions as percentages of total range
const RED_END_PCT = RED_WIDTH;      // 20%
const ORANGE_END_PCT = RED_WIDTH + ORANGE_WIDTH; // 60%

interface SavingsRateGaugeProps {
  value: number;
}

const SavingsRateGauge = ({ value }: SavingsRateGaugeProps) => {
  // Clamp value within displayable range for arrow positioning
  const clamped = Math.max(0, Math.min(value, RANGE_MAX));
  const arrowLeft = `${(clamped / RANGE_MAX) * 100}%`;

  return (
    <div
      className="w-full mt-3 relative"
      aria-label={`Норма сбережений: ${value}%`}
    >
      {/* Arrow indicator above gauge track */}
      <div
        style={{
          position: 'absolute',
          left: arrowLeft,
          top: 0,
          transform: 'translateX(-50%)',
          transition: 'left 0.6s ease',
          lineHeight: 1,
        }}
      >
        {/* CSS triangle pointing down */}
        <div
          style={{
            width: 0,
            height: 0,
            borderLeft: '5px solid transparent',
            borderRight: '5px solid transparent',
            borderTop: '6px solid rgba(255,255,255,0.75)',
          }}
        />
      </div>

      {/* Gauge track — flex row of three colored sections */}
      <div className="flex w-full mt-2" style={{ height: 8 }}>
        {/* Red: 0–10 % (20% of range) */}
        <div
          className="rounded-l-full"
          style={{
            width: `${RED_WIDTH}%`,
            backgroundColor: '#EF4444',
            height: '100%',
          }}
        />
        {/* Orange: 10–30 % (40% of range) */}
        <div
          style={{
            width: `${ORANGE_WIDTH}%`,
            backgroundColor: '#F59E0B',
            height: '100%',
          }}
        />
        {/* Green: 30–50 % (40% of range) */}
        <div
          className="rounded-r-full"
          style={{
            width: `${GREEN_WIDTH}%`,
            backgroundColor: '#10B981',
            height: '100%',
          }}
        />
      </div>

      {/* Threshold labels below the track */}
      <div className="relative mt-1" style={{ height: 14 }}>
        <span
          className="absolute text-[10px] text-dashboard-text-muted"
          style={{ left: `${RED_END_PCT}%`, transform: 'translateX(-50%)' }}
        >
          10%
        </span>
        <span
          className="absolute text-[10px] text-dashboard-text-muted"
          style={{ left: `${ORANGE_END_PCT}%`, transform: 'translateX(-50%)' }}
        >
          30%
        </span>
      </div>
    </div>
  );
};

export default SavingsRateGauge;

import { useState, useEffect } from 'react';
import { TrendingUp, TrendingDown, PieChart, Coins } from 'lucide-react';
import AddAssetDialog from '@/components/AddAssetDialog';

const DONUT_COLORS = ['#10B981', '#3B82F6', '#F59E0B', '#8B5CF6', '#EC4899', '#06B6D4', '#F97316', '#14B8A6'];
const DANGER_COLOR = '#EF4444';
const formatCurrency = (value: number) => value.toLocaleString('ru-RU') + ' \u20BD';

// Animated count-up hook with easeOutCubic easing
const useCountUp = (target: number, duration = 800) => {
  const [value, setValue] = useState(0);
  useEffect(() => {
    if (target === 0) { setValue(0); return; }
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) { setValue(target); return; }
    let rafId: number;
    const start = performance.now();
    const step = (now: number) => {
      const progress = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      setValue(Math.round(target * eased));
      if (progress < 1) rafId = requestAnimationFrame(step);
    };
    rafId = requestAnimationFrame(step);
    return () => cancelAnimationFrame(rafId);
  }, [target, duration]);
  return value;
};

const pluralAssets = (n: number) => {
  const mod10 = n % 10;
  const mod100 = n % 100;
  if (mod10 === 1 && mod100 !== 11) return `${n} актив`;
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return `${n} актива`;
  return `${n} активов`;
};

const Investments = () => {
  // Примерные данные портфеля
  const totalValue = 2850000;
  const totalGain = 385000;
  const gainPercentage = 15.6;
  const monthlyGain = 42000;

  const [holdings, setHoldings] = useState([
    {
      symbol: 'AAPL',
      name: 'Apple Inc.',
      shares: 50,
      price: 175.5,
      value: 8775,
      change: 2.3,
      changePercent: 1.33,
      sector: 'Технологии'
    },
    {
      symbol: 'MSFT',
      name: 'Microsoft Corp.',
      shares: 30,
      price: 378.85,
      value: 11365.5,
      change: -5.2,
      changePercent: -1.35,
      sector: 'Технологии'
    },
    {
      symbol: 'GOOGL',
      name: 'Alphabet Inc.',
      shares: 25,
      price: 142.8,
      value: 3570,
      change: 8.7,
      changePercent: 6.49,
      sector: 'Технологии'
    },
    {
      symbol: 'TSLA',
      name: 'Tesla Inc.',
      shares: 15,
      price: 248.5,
      value: 3727.5,
      change: -12.3,
      changePercent: -4.72,
      sector: 'Автомобили'
    },
    {
      symbol: 'NVDA',
      name: 'NVIDIA Corp.',
      shares: 20,
      price: 875.2,
      value: 17504,
      change: 25.8,
      changePercent: 3.04,
      sector: 'Технологии'
    },
    {
      symbol: 'JPM',
      name: 'JPMorgan Chase',
      shares: 40,
      price: 145.2,
      value: 5808,
      change: 1.8,
      changePercent: 1.25,
      sector: 'Финансы'
    },
    {
      symbol: 'JNJ',
      name: 'Johnson & Johnson',
      shares: 35,
      price: 158.7,
      value: 5554.5,
      change: -0.5,
      changePercent: -0.31,
      sector: 'Здравоохранение'
    }
  ]);

  const handleAddAsset = (newAsset: any) => {
    setHoldings(prev => [...prev, newAsset]);
  };

  // Группировка акций по секторам
  const holdingsBySector = holdings.reduce((acc, holding) => {
    if (!acc[holding.sector]) {
      acc[holding.sector] = [];
    }
    acc[holding.sector].push(holding);
    return acc;
  }, {} as Record<string, typeof holdings>);

  const sectors = [
    { name: 'Технологии', percentage: 45, value: 1282500, color: DONUT_COLORS[0] },
    { name: 'Финансы', percentage: 20, value: 570000, color: DONUT_COLORS[1] },
    { name: 'Здравоохранение', percentage: 15, value: 427500, color: DONUT_COLORS[3] },
    { name: 'Потребительские товары', percentage: 12, value: 342000, color: DONUT_COLORS[2] },
    { name: 'Прочее', percentage: 8, value: 228000, color: DONUT_COLORS[4] },
  ];

  // Animated KPI values (count-up from 0)
  const animTotalValue = useCountUp(totalValue);
  const animTotalGain = useCountUp(totalGain);
  const animMonthlyGain = useCountUp(monthlyGain);
  const animDividends = useCountUp(48500);

  const kpiCards = [
    { label: 'СТОИМОСТЬ ПОРТФЕЛЯ', value: formatCurrency(animTotalValue), icon: PieChart, color: '#3B82F6', glow: 'rgba(59, 130, 246, 0.3)' },
    { label: 'ОБЩАЯ ДОХОДНОСТЬ', value: formatCurrency(animTotalGain), icon: TrendingUp, color: '#10B981', glow: 'rgba(16, 185, 129, 0.3)', trend: { value: `+${gainPercentage}%`, isPositive: true } },
    { label: 'ЗА МЕСЯЦ', value: formatCurrency(animMonthlyGain), icon: TrendingUp, color: '#8B5CF6', glow: 'rgba(139, 92, 246, 0.3)', trend: { value: '+8.5%', isPositive: true } },
    { label: 'ДИВИДЕНДЫ', value: formatCurrency(animDividends), icon: Coins, color: '#F59E0B', glow: 'rgba(245, 158, 11, 0.3)', trend: { value: '+12.3%', isPositive: true } }, // TODO: connect to dividends API
  ];

  const holdingColorIndex = new Map<string, number>();
  Object.values(holdingsBySector).flat().forEach((h, i) => holdingColorIndex.set(h.symbol, i));

  return (
    <div className="space-y-6 pb-6">

      {/* KPI Cards */}
      <div className="flex gap-4 overflow-x-auto snap-x snap-mandatory pb-2 -mx-4 px-4 lg:mx-0 lg:px-0 lg:grid lg:grid-cols-2 xl:grid-cols-4 lg:gap-5 lg:overflow-visible lg:pb-0 hide-scrollbar">
        {kpiCards.map((card, index) => {
          const Icon = card.icon;
          const isNegativeTrend = card.trend && !card.trend.isPositive;

          return (
            <div
              key={card.label}
              className="glass-card p-5 flex items-start justify-between group transition-all duration-300 hover:scale-[1.02] animate-fade-slide-up min-w-[260px] snap-start lg:min-w-0"
              style={{
                borderLeft: `3px solid ${isNegativeTrend ? DANGER_COLOR : card.color}`,
                animationDelay: `${index * 60}ms`,
              }}
            >
              <div className="space-y-2">
                <p className="text-[11px] font-semibold tracking-widest text-dashboard-text-muted">
                  {card.label}
                </p>
                <p className="text-2xl font-bold text-dashboard-text font-mono">{card.value}</p>
                {card.trend && (
                  <div className={`flex items-center gap-1 text-xs font-medium ${card.trend.isPositive ? 'text-emerald-400' : 'text-red-400'}`}>
                    {card.trend.isPositive ? <TrendingUp className="w-3.5 h-3.5" /> : <TrendingDown className="w-3.5 h-3.5" />}
                    <span className="font-mono">{card.trend.value}</span>
                  </div>
                )}
              </div>
              <div
                className="w-11 h-11 rounded-xl flex items-center justify-center shrink-0"
                style={{ backgroundColor: `${card.color}20`, boxShadow: `0 0 20px ${card.glow}` }}
              >
                <Icon className="w-5 h-5" style={{ color: card.color }} />
              </div>
            </div>
          );
        })}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        {/* Портфель */}
        <div className="lg:col-span-2">
          <div className="glass-card p-5 animate-fade-slide-up" style={{ animationDelay: '300ms' }}>
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-sm font-semibold text-dashboard-text">Мои активы</h3>
              <AddAssetDialog onAddAsset={handleAddAsset} />
            </div>
            <div className="space-y-6 max-h-[400px] overflow-y-auto dashboard-scroll pr-1">
              {Object.entries(holdingsBySector).map(([sectorName, sectorHoldings], sectorIdx) => {
                const sectorValue = sectorHoldings.reduce((sum, holding) => sum + holding.value, 0);
                const sectorPercentage = ((sectorValue / totalValue) * 100).toFixed(1);

                return (
                <div key={sectorName} className="space-y-3 animate-fade-slide-up" style={{ animationDelay: `${400 + sectorIdx * 80}ms` }}>
                  <div className="flex items-center justify-between border-b border-white/10 pb-2">
                    <h3 className="font-semibold text-dashboard-text">{sectorName}</h3>
                    <div className="flex items-center gap-3">
                      <span className="text-sm font-medium font-mono text-dashboard-text">
                        {sectorPercentage}%
                      </span>
                      <span className="text-sm text-dashboard-text-muted">
                        {pluralAssets(sectorHoldings.length)}
                      </span>
                    </div>
                  </div>
                  <div className="space-y-3">
                    {sectorHoldings.map((holding) => {
                      const colorIdx = holdingColorIndex.get(holding.symbol)! % DONUT_COLORS.length;
                      return (
                        <div key={holding.symbol} className="flex items-center justify-between p-3 bg-white/[0.03] rounded-lg hover:bg-white/[0.07] transition-all duration-200">
                          <div className="flex items-center space-x-4">
                            <div
                              className="w-10 h-10 rounded-full flex items-center justify-center"
                              style={{ backgroundColor: `${DONUT_COLORS[colorIdx]}20` }}
                            >
                              <span
                                className="font-bold text-sm"
                                style={{ color: DONUT_COLORS[colorIdx] }}
                              >
                                {holding.symbol.slice(0, 2)}
                              </span>
                            </div>
                            <div>
                              <div className="font-semibold text-dashboard-text">{holding.symbol}</div>
                              <div className="text-sm text-dashboard-text-muted">{holding.name}</div>
                              <div className="text-xs text-dashboard-text-muted font-mono">
                                {holding.shares} акций × {formatCurrency(holding.price)}
                              </div>
                            </div>
                          </div>
                          <div className="text-right">
                            <div className="font-semibold text-dashboard-text font-mono">{formatCurrency(holding.value)}</div>
                            <div className={`text-sm flex items-center justify-end font-mono ${
                              holding.changePercent >= 0 ? 'text-emerald-400' : 'text-red-400'
                            }`}>
                              {holding.changePercent >= 0 ? (
                                <TrendingUp className="w-3 h-3 mr-1" />
                              ) : (
                                <TrendingDown className="w-3 h-3 mr-1" />
                              )}
                              {holding.changePercent >= 0 ? '+' : ''}{holding.changePercent}%
                            </div>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
                );
              })}
            </div>
          </div>
        </div>

        {/* Распределение по секторам */}
        <div className="glass-card p-5 animate-fade-slide-up" style={{ animationDelay: '380ms' }}>
          <h3 className="text-sm font-semibold text-dashboard-text mb-4">Распределение по секторам</h3>
          <div className="space-y-4">
            {sectors.map((sector, index) => (
              <div key={index} className="space-y-2">
                <div className="flex justify-between items-center">
                  <div className="flex items-center space-x-3">
                    <div
                      className="w-3 h-3 rounded-full"
                      style={{ backgroundColor: sector.color }}
                    />
                    <span className="font-medium text-sm text-dashboard-text">{sector.name}</span>
                  </div>
                  <div className="text-right">
                    <div className="font-semibold text-sm text-dashboard-text font-mono">
                      {sector.percentage}%
                    </div>
                    <div className="text-xs text-dashboard-text-muted font-mono">
                      {formatCurrency(sector.value)}
                    </div>
                  </div>
                </div>
                <div className="w-full bg-white/5 rounded-full h-2">
                  <div
                    className="h-2 rounded-full animate-progress-grow"
                    style={{ width: `${sector.percentage}%`, backgroundColor: sector.color, animationDelay: `${500 + index * 80}ms` }}
                  />
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};

export default Investments;

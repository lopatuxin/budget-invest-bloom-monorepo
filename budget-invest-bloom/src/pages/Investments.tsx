import { useState } from 'react';
import { TrendingUp, TrendingDown, PieChart } from 'lucide-react';
import AddAssetDialog from '@/components/AddAssetDialog';

const DONUT_COLORS = ['#10B981', '#3B82F6', '#F59E0B', '#8B5CF6', '#EC4899', '#06B6D4', '#F97316', '#14B8A6'];
const formatCurrency = (value: number) => value.toLocaleString('ru-RU') + ' \u20BD';

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

  const kpiCards = [
    { label: 'ОБЩАЯ СТОИМОСТЬ', value: formatCurrency(totalValue), icon: PieChart, color: '#3B82F6', glow: 'rgba(59, 130, 246, 0.3)' },
    { label: 'ПРИБЫЛЬ/УБЫТОК', value: formatCurrency(totalGain), icon: TrendingUp, color: '#10B981', glow: 'rgba(16, 185, 129, 0.3)', trend: { value: `+${gainPercentage}%`, isPositive: true } },
    { label: 'ЗА МЕСЯЦ', value: formatCurrency(monthlyGain), icon: TrendingUp, color: '#8B5CF6', glow: 'rgba(139, 92, 246, 0.3)', trend: { value: '+8.5%', isPositive: true } },
  ];

  const holdingColorIndex = new Map<string, number>();
  Object.values(holdingsBySector).flat().forEach((h, i) => holdingColorIndex.set(h.symbol, i));

  return (
    <div className="space-y-6 pb-6">

      {/* KPI Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
        {kpiCards.map(card => {
          const Icon = card.icon;
          return (
            <div
              key={card.label}
              className="glass-card p-5 flex items-start justify-between group transition-all duration-300 hover:scale-[1.02]"
            >
              <div className="space-y-2">
                <p className="text-[11px] font-semibold tracking-widest text-dashboard-text-muted">
                  {card.label}
                </p>
                <p className="text-2xl font-bold text-dashboard-text">{card.value}</p>
                {card.trend && (
                  <div className={`flex items-center gap-1 text-xs font-medium ${card.trend.isPositive ? 'text-emerald-400' : 'text-red-400'}`}>
                    {card.trend.isPositive ? <TrendingUp className="w-3.5 h-3.5" /> : <TrendingDown className="w-3.5 h-3.5" />}
                    <span>{card.trend.value}</span>
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
          <div className="glass-card p-5">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-sm font-semibold text-dashboard-text">Мои активы</h3>
              <AddAssetDialog onAddAsset={handleAddAsset} />
            </div>
            <div className="space-y-6 max-h-[400px] overflow-y-auto dashboard-scroll pr-1">
              {Object.entries(holdingsBySector).map(([sectorName, sectorHoldings]) => {
                const sectorValue = sectorHoldings.reduce((sum, holding) => sum + holding.value, 0);
                const sectorPercentage = ((sectorValue / totalValue) * 100).toFixed(1);

                return (
                <div key={sectorName} className="space-y-3">
                  <div className="flex items-center justify-between border-b border-white/10 pb-2">
                    <h3 className="font-semibold text-dashboard-text">{sectorName}</h3>
                    <div className="flex items-center gap-3">
                      <span className="text-sm font-medium text-dashboard-text">
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
                              <div className="text-xs text-dashboard-text-muted">
                                {holding.shares} акций × {formatCurrency(holding.price)}
                              </div>
                            </div>
                          </div>
                          <div className="text-right">
                            <div className="font-semibold text-dashboard-text">{formatCurrency(holding.value)}</div>
                            <div className={`text-sm flex items-center justify-end ${
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
        <div className="glass-card p-5">
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
                    <div className="font-semibold text-sm text-dashboard-text">
                      {sector.percentage}%
                    </div>
                    <div className="text-xs text-dashboard-text-muted">
                      {formatCurrency(sector.value)}
                    </div>
                  </div>
                </div>
                <div className="w-full bg-white/5 rounded-full h-2">
                  <div
                    className="h-2 rounded-full transition-all duration-500"
                    style={{ width: `${sector.percentage}%`, backgroundColor: sector.color }}
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

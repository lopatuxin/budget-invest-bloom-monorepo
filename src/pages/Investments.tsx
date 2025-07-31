import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { TrendingUp, TrendingDown, Plus, PieChart } from 'lucide-react';
import FinanceCard from '@/components/FinanceCard';

const Investments = () => {
  // Примерные данные портфеля
  const totalValue = 2850000;
  const totalGain = 385000;
  const gainPercentage = 15.6;
  const monthlyGain = 42000;

  const holdings = [
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
  ];

  // Группировка акций по секторам
  const holdingsBySector = holdings.reduce((acc, holding) => {
    if (!acc[holding.sector]) {
      acc[holding.sector] = [];
    }
    acc[holding.sector].push(holding);
    return acc;
  }, {} as Record<string, typeof holdings>);

  const sectors = [
    { name: 'Технологии', percentage: 45, value: 1282500, color: 'bg-blue-500' },
    { name: 'Финансы', percentage: 20, value: 570000, color: 'bg-green-500' },
    { name: 'Здравоохранение', percentage: 15, value: 427500, color: 'bg-purple-500' },
    { name: 'Потребительские товары', percentage: 12, value: 342000, color: 'bg-orange-500' },
    { name: 'Прочее', percentage: 8, value: 228000, color: 'bg-gray-500' },
  ];

  return (
    <div className="min-h-screen bg-gradient-background">
      <div className="max-w-7xl mx-auto p-6">
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-foreground mb-2">Инвестиции</h1>
          <p className="text-muted-foreground">Отслеживайте свой инвестиционный портфель</p>
        </div>

        {/* Карточки обзора портфеля */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
          <FinanceCard
            title="Общая стоимость"
            value={`₽${totalValue.toLocaleString()}`}
            icon={<PieChart className="w-5 h-5" />}
            gradient="primary"
          />
          <FinanceCard
            title="Прибыль/Убыток"
            value={`₽${totalGain.toLocaleString()}`}
            icon={<TrendingUp className="w-5 h-5" />}
            trend={{ value: `+${gainPercentage}%`, isPositive: true }}
            gradient="success"
          />
          <FinanceCard
            title="За месяц"
            value={`₽${monthlyGain.toLocaleString()}`}
            icon={<TrendingUp className="w-5 h-5" />}
            trend={{ value: "+8.5%", isPositive: true }}
            gradient="secondary"
          />
          <div className="flex justify-center items-center">
            <Button 
              className="bg-gradient-primary hover:opacity-90 h-16 px-8"
              size="lg"
            >
              <Plus className="w-5 h-5 mr-2" />
              Добавить актив
            </Button>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Портфель */}
          <div className="lg:col-span-2">
            <Card className="shadow-card border-0">
              <CardHeader>
                <CardTitle>Мои активы</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="space-y-6">
                  {Object.entries(holdingsBySector).map(([sectorName, sectorHoldings]) => {
                    const sectorValue = sectorHoldings.reduce((sum, holding) => sum + holding.value, 0);
                    const sectorPercentage = ((sectorValue / totalValue) * 100).toFixed(1);
                    
                    return (
                    <div key={sectorName} className="space-y-3">
                      <div className="flex items-center justify-between border-b border-muted pb-2">
                        <h3 className="font-semibold text-primary">{sectorName}</h3>
                        <div className="flex items-center gap-3">
                          <span className="text-sm font-medium text-foreground">
                            {sectorPercentage}%
                          </span>
                          <span className="text-sm text-muted-foreground">
                            {sectorHoldings.length} активов
                          </span>
                        </div>
                      </div>
                      <div className="space-y-3">
                        {sectorHoldings.map((holding, index) => (
                          <div key={index} className="flex items-center justify-between p-3 bg-muted/20 rounded-lg hover:bg-muted/40 transition-all duration-200">
                            <div className="flex items-center space-x-4">
                              <div className="w-10 h-10 bg-gradient-primary rounded-full flex items-center justify-center">
                                <span className="text-primary-foreground font-bold text-sm">
                                  {holding.symbol.slice(0, 2)}
                                </span>
                              </div>
                              <div>
                                <div className="font-semibold">{holding.symbol}</div>
                                <div className="text-sm text-muted-foreground">{holding.name}</div>
                                <div className="text-xs text-muted-foreground">
                                  {holding.shares} акций × ₽{holding.price}
                                </div>
                              </div>
                            </div>
                            <div className="text-right">
                              <div className="font-semibold">₽{holding.value.toLocaleString()}</div>
                              <div className={`text-sm flex items-center justify-end ${
                                holding.changePercent >= 0 ? 'text-primary' : 'text-destructive'
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
                        ))}
                      </div>
                    </div>
                    );
                  })}
                </div>
              </CardContent>
            </Card>
          </div>

          {/* Распределение по секторам */}
          <Card className="shadow-card border-0">
            <CardHeader>
              <CardTitle>Распределение по секторам</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {sectors.map((sector, index) => (
                <div key={index} className="space-y-2">
                  <div className="flex justify-between items-center">
                    <div className="flex items-center space-x-3">
                      <div className={`w-3 h-3 rounded-full ${sector.color}`} />
                      <span className="font-medium text-sm">{sector.name}</span>
                    </div>
                    <div className="text-right">
                      <div className="font-semibold text-sm">
                        {sector.percentage}%
                      </div>
                      <div className="text-xs text-muted-foreground">
                        ₽{sector.value.toLocaleString()}
                      </div>
                    </div>
                  </div>
                  <div className="w-full bg-muted rounded-full h-2">
                    <div
                      className="h-2 rounded-full bg-gradient-primary transition-all duration-500"
                      style={{ width: `${sector.percentage}%` }}
                    />
                  </div>
                </div>
              ))}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
};

export default Investments;
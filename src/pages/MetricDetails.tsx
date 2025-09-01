import { useParams, useNavigate } from 'react-router-dom';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { ArrowLeft, TrendingUp, TrendingDown, DollarSign, Wallet, CreditCard, Plus, Minus } from 'lucide-react';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';

const MetricDetails = () => {
  const { metric } = useParams<{ metric: string }>();
  const navigate = useNavigate();

  // Данные для графиков (примерные данные за последние 12 месяцев)
  const monthlyData = [
    { month: 'Янв', income: 145000, expenses: 85000, balance: 60000, capital: 800000, inflation: 6.2 },
    { month: 'Фев', income: 148000, expenses: 87000, balance: 61000, capital: 820000, inflation: 6.4 },
    { month: 'Мар', income: 147000, expenses: 89000, balance: 58000, capital: 835000, inflation: 6.5 },
    { month: 'Апр', income: 149000, expenses: 88000, balance: 61000, capital: 845000, inflation: 6.3 },
    { month: 'Май', income: 150000, expenses: 90000, balance: 60000, capital: 850000, inflation: 6.6 },
    { month: 'Июн', income: 152000, expenses: 91000, balance: 61000, capital: 855000, inflation: 6.7 },
    { month: 'Июл', income: 151000, expenses: 89000, balance: 62000, capital: 860000, inflation: 6.5 },
    { month: 'Авг', income: 153000, expenses: 92000, balance: 61000, capital: 865000, inflation: 6.8 },
    { month: 'Сен', income: 149000, expenses: 88000, balance: 61000, capital: 870000, inflation: 6.7 },
    { month: 'Окт', income: 151000, expenses: 90000, balance: 61000, capital: 872000, inflation: 6.9 },
    { month: 'Ноя', income: 148000, expenses: 89000, balance: 59000, capital: 874000, inflation: 6.8 },
    { month: 'Дек', income: 150000, expenses: 89500, balance: 60500, capital: 875000, inflation: 6.8 },
  ];

  const getMetricConfig = () => {
    switch (metric) {
      case 'income':
        return {
          title: 'Доходы',
          icon: <Plus className="w-6 h-6" />,
          dataKey: 'income',
          color: '#22c55e',
          gradient: 'success',
          description: 'Ваши ежемесячные доходы'
        };
      case 'expenses':
        return {
          title: 'Расходы',
          icon: <Minus className="w-6 h-6" />,
          dataKey: 'expenses',
          color: '#ef4444',
          gradient: 'secondary',
          description: 'Ваши ежемесячные расходы'
        };
      case 'balance':
        return {
          title: 'Остаток',
          icon: <DollarSign className="w-6 h-6" />,
          dataKey: 'balance',
          color: '#3b82f6',
          gradient: 'primary',
          description: 'Разница между доходами и расходами'
        };
      case 'capital':
        return {
          title: 'Капитал',
          icon: <Wallet className="w-6 h-6" />,
          dataKey: 'capital',
          color: '#8b5cf6',
          gradient: 'primary',
          description: 'Общая стоимость ваших активов'
        };
      case 'inflation':
        return {
          title: 'Личная инфляция',
          icon: <TrendingUp className="w-6 h-6" />,
          dataKey: 'inflation',
          color: '#f59e0b',
          gradient: 'secondary',
          description: 'Рост ваших личных расходов в процентах'
        };
      default:
        return {
          title: 'Неизвестная метрика',
          icon: <CreditCard className="w-6 h-6" />,
          dataKey: 'balance',
          color: '#6b7280',
          gradient: 'primary',
          description: ''
        };
    }
  };

  const config = getMetricConfig();
  const currentValue = monthlyData[monthlyData.length - 1][config.dataKey as keyof typeof monthlyData[0]];
  const previousValue = monthlyData[monthlyData.length - 2][config.dataKey as keyof typeof monthlyData[0]];
  const change = ((Number(currentValue) - Number(previousValue)) / Number(previousValue)) * 100;

  const formatValue = (value: number) => {
    if (metric === 'inflation') {
      return `${value}%`;
    }
    return `₽${value.toLocaleString()}`;
  };

  return (
    <div className="h-[calc(100vh-4rem)] bg-gradient-background overflow-auto">
      <div className="max-w-7xl mx-auto p-6">
        <div className="flex items-center gap-4 mb-6">
          <Button 
            variant="outline" 
            size="sm"
            onClick={() => navigate('/budget')}
            className="flex items-center gap-2"
          >
            <ArrowLeft className="w-4 h-4" />
            Назад к бюджету
          </Button>
          <div className="flex items-center gap-3">
            <div className={`p-3 rounded-lg bg-gradient-${config.gradient}`}>
              <div className="text-primary-foreground">
                {config.icon}
              </div>
            </div>
            <div>
              <h1 className="text-2xl font-bold">{config.title}</h1>
              <p className="text-muted-foreground">{config.description}</p>
            </div>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-8">
          <Card className="shadow-card border-0">
            <CardHeader>
              <CardTitle className="text-sm text-muted-foreground">Текущее значение</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-3xl font-bold">{formatValue(Number(currentValue))}</div>
              <div className={`flex items-center text-sm mt-2 ${change >= 0 ? 'text-primary' : 'text-destructive'}`}>
                {change >= 0 ? <TrendingUp className="w-4 h-4 mr-1" /> : <TrendingDown className="w-4 h-4 mr-1" />}
                {change >= 0 ? '+' : ''}{change.toFixed(1)}% за месяц
              </div>
            </CardContent>
          </Card>

          <Card className="shadow-card border-0">
            <CardHeader>
              <CardTitle className="text-sm text-muted-foreground">Среднее за год</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-3xl font-bold">
                {formatValue(monthlyData.reduce((sum, item) => sum + Number(item[config.dataKey as keyof typeof item]), 0) / monthlyData.length)}
              </div>
            </CardContent>
          </Card>

          <Card className="shadow-card border-0">
            <CardHeader>
              <CardTitle className="text-sm text-muted-foreground">Максимум за год</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-3xl font-bold">
                {formatValue(Math.max(...monthlyData.map(item => Number(item[config.dataKey as keyof typeof item]))))}
              </div>
            </CardContent>
          </Card>
        </div>

        <Card className="shadow-card border-0 mb-8">
          <CardHeader>
            <CardTitle>График по месяцам</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="h-[400px]">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={monthlyData}>
                  <CartesianGrid strokeDasharray="3 3" className="opacity-30" />
                  <XAxis dataKey="month" />
                  <YAxis tickFormatter={(value) => metric === 'inflation' ? `${value}%` : `₽${(value / 1000).toFixed(0)}k`} />
                  <Tooltip 
                    formatter={(value) => [formatValue(Number(value)), config.title]}
                    labelFormatter={(label) => `${label} 2024`}
                  />
                  <Line 
                    type="monotone" 
                    dataKey={config.dataKey} 
                    stroke={config.color} 
                    strokeWidth={3}
                    dot={{ fill: config.color, strokeWidth: 2, r: 6 }}
                    activeDot={{ r: 8, stroke: config.color, strokeWidth: 2 }}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        <Card className="shadow-card border-0">
          <CardHeader>
            <CardTitle>Данные по месяцам</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {monthlyData.map((data, index) => (
                <div key={index} className="p-4 rounded-lg border border-border/20 hover:border-border/40 transition-colors">
                  <div className="flex justify-between items-center">
                    <span className="font-medium">{data.month} 2024</span>
                    <span className="text-lg font-bold">
                      {formatValue(Number(data[config.dataKey as keyof typeof data]))}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

export default MetricDetails;
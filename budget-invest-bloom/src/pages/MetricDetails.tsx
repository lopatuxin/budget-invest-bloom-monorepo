import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { ArrowLeft, TrendingUp, TrendingDown, DollarSign, Wallet, CreditCard, Plus, Minus, Loader2, AlertCircle } from 'lucide-react';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { useIncomeMetric } from '@/hooks/useIncomeMetric';
import { useExpenseMetric } from '@/hooks/useExpenseMetric';
import { useBalanceMetric } from '@/hooks/useBalanceMetric';
import { useCapitalMetric } from '@/hooks/useCapitalMetric';
import { useInflationMetric } from '@/hooks/useInflationMetric';
import EmptyState from '@/components/EmptyState';
import { getYearOptions } from '@/lib/dateOptions';

const MetricDetails = () => {
  const { metric } = useParams<{ metric: string }>();
  const navigate = useNavigate();
  const [selectedYear, setSelectedYear] = useState(String(new Date().getFullYear()));

  const years = getYearOptions();

  const { data: incomeMetric, isLoading: incomeLoading, error: incomeError } = useIncomeMetric(selectedYear, metric === 'income');
  const { data: expenseMetric, isLoading: expenseLoading, error: expenseError } = useExpenseMetric(selectedYear, metric === 'expenses');
  const { data: balanceMetric, isLoading: balanceLoading, error: balanceError } = useBalanceMetric(selectedYear, metric === 'balance');
  const { data: capitalMetric, isLoading: capitalLoading, error: capitalError } = useCapitalMetric(selectedYear, metric === 'capital');
  const { data: inflationMetric, isLoading: inflationLoading, error: inflationError } = useInflationMetric(selectedYear, metric === 'inflation');

  const monthlyData = metric === 'income' && incomeMetric?.body
    ? incomeMetric.body.monthlyData.map(item => ({
        month: item.monthName,
        income: item.amount,
        expenses: 0, balance: 0, capital: 0, inflation: 0,
      }))
    : metric === 'expenses' && expenseMetric?.body
    ? expenseMetric.body.monthlyData.map(item => ({
        month: item.monthName,
        expenses: item.amount,
        income: 0, balance: 0, capital: 0, inflation: 0,
      }))
    : metric === 'balance' && balanceMetric?.body
    ? balanceMetric.body.monthlyData.map(item => ({
        month: item.monthName,
        balance: item.amount,
        income: 0, expenses: 0, capital: 0, inflation: 0,
      }))
    : metric === 'capital' && capitalMetric?.body
    ? capitalMetric.body.monthlyData.map(item => ({
        month: item.monthName,
        capital: item.amount,
        income: 0, expenses: 0, balance: 0, inflation: 0,
      }))
    : metric === 'inflation' && inflationMetric?.body
    ? inflationMetric.body.monthlyData.map(item => ({
        month: item.monthName,
        inflation: item.amount,
        income: 0, expenses: 0, balance: 0, capital: 0,
      }))
    : [];

  const getMetricConfig = () => {
    switch (metric) {
      case 'income':
        return {
          title: 'Доходы',
          icon: <Plus className="w-5 h-5" />,
          dataKey: 'income',
          color: '#10B981',
          glow: 'rgba(16, 185, 129, 0.3)',
          description: 'Ваши ежемесячные доходы'
        };
      case 'expenses':
        return {
          title: 'Расходы',
          icon: <Minus className="w-5 h-5" />,
          dataKey: 'expenses',
          color: '#F59E0B',
          glow: 'rgba(245, 158, 11, 0.3)',
          description: 'Ваши ежемесячные расходы'
        };
      case 'balance':
        return {
          title: 'Остаток',
          icon: <DollarSign className="w-5 h-5" />,
          dataKey: 'balance',
          color: '#3B82F6',
          glow: 'rgba(59, 130, 246, 0.3)',
          description: 'Разница между доходами и расходами'
        };
      case 'capital':
        return {
          title: 'Капитал',
          icon: <Wallet className="w-5 h-5" />,
          dataKey: 'capital',
          color: '#8B5CF6',
          glow: 'rgba(139, 92, 246, 0.3)',
          description: 'Общая стоимость ваших активов'
        };
      case 'inflation':
        return {
          title: 'Личная инфляция',
          icon: <TrendingUp className="w-5 h-5" />,
          dataKey: 'inflation',
          color: '#EC4899',
          glow: 'rgba(236, 72, 153, 0.3)',
          description: 'Рост ваших средних расходов в месяц по сравнению с прошлым годом. Каждая категория влияет на итог пропорционально своей доле в расходах.'
        };
      default:
        return null;
    }
  };

  const config = getMetricConfig();

  // Unknown metric — show empty state
  if (config === null) {
    return (
      <div className="h-[calc(100vh-4rem)] flex items-center justify-center">
        <EmptyState
          icon={<CreditCard className="w-10 h-10" />}
          title="Неизвестная метрика"
          description="Запрошенная метрика не существует"
        />
      </div>
    );
  }

  const activeMetric = metric === 'income' ? incomeMetric : metric === 'expenses' ? expenseMetric : metric === 'balance' ? balanceMetric : metric === 'capital' ? capitalMetric : metric === 'inflation' ? inflationMetric : null;
  const activeLoading = metric === 'income' ? incomeLoading : metric === 'expenses' ? expenseLoading : metric === 'balance' ? balanceLoading : metric === 'capital' ? capitalLoading : metric === 'inflation' ? inflationLoading : false;
  const activeError = metric === 'income' ? incomeError : metric === 'expenses' ? expenseError : metric === 'balance' ? balanceError : metric === 'capital' ? capitalError : metric === 'inflation' ? inflationError : null;

  // Helper to extract the active dataKey value from a monthly data entry
  const getValue = (item: typeof monthlyData[0]) => Number(item[config.dataKey as keyof typeof item]);

  const { currentValue, change } = (() => {
    if (activeMetric?.body) {
      const parsed = parseFloat(activeMetric.body.changePercent.replace('%', '').replace('+', ''));
      return { currentValue: activeMetric.body.currentValue, change: isNaN(parsed) ? 0 : parsed };
    }
    if (monthlyData.length >= 2) {
      const cur = getValue(monthlyData[monthlyData.length - 1]);
      const prev = getValue(monthlyData[monthlyData.length - 2]);
      return { currentValue: cur, change: prev === 0 ? 0 : ((cur - prev) / prev) * 100 };
    }
    return { currentValue: 0, change: 0 };
  })();

  const formatValue = (value: number) => {
    if (metric === 'inflation') {
      return `${value}%`;
    }
    return `₽${value.toLocaleString()}`;
  };

  return (
    <div className="h-[calc(100vh-4rem)] overflow-auto dashboard-scroll">
      <div className="max-w-7xl mx-auto p-6">
        <div className="flex items-center justify-between gap-4 mb-6">
          <div className="flex items-center gap-4">
            <Button
              variant="outline"
              size="sm"
              onClick={() => navigate('/budget')}
              className="flex items-center gap-2 text-dashboard-text-muted hover:text-dashboard-text hover:bg-white/5 border-white/10"
            >
              <ArrowLeft className="w-4 h-4" />
              Назад к бюджету
            </Button>
            <div className="flex items-center gap-3">
              <div
                className="w-11 h-11 rounded-xl flex items-center justify-center shrink-0"
                style={{ backgroundColor: `${config.color}20`, boxShadow: `0 0 20px ${config.glow}` }}
              >
                <div style={{ color: config.color }}>
                  {config.icon}
                </div>
              </div>
              <div>
                <h1 className="text-2xl font-bold text-dashboard-text">{config.title}</h1>
                <p className="text-dashboard-text-muted">{config.description}</p>
              </div>
            </div>
          </div>
          <Select value={selectedYear} onValueChange={setSelectedYear}>
            <SelectTrigger className="w-[100px] bg-white/5 border-white/10 text-dashboard-text hover:bg-white/[0.08] transition-colors">
              <SelectValue placeholder="Год" />
            </SelectTrigger>
            <SelectContent>
              {years.map((year) => (
                <SelectItem key={year} value={year}>
                  {year}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {activeError ? (
          <div className="glass-card max-w-md w-full mx-auto">
            <CardContent className="flex flex-col items-center gap-4 pt-6">
              <AlertCircle className="w-12 h-12 text-red-400" />
              <p className="text-lg font-medium text-center text-dashboard-text">Не удалось загрузить данные: {config.title.toLowerCase()}</p>
              <p className="text-sm text-dashboard-text-muted text-center">{activeError.message}</p>
            </CardContent>
          </div>
        ) : activeLoading ? (
          <div className="flex items-center justify-center py-24">
            <Loader2 className="w-8 h-8 animate-spin text-dashboard-text-muted" />
          </div>
        ) : monthlyData.length === 0 ? (
          <EmptyState
            icon={<AlertCircle className="w-10 h-10" />}
            title="Нет данных"
            description={`Данные по метрике «${config.title}» за ${selectedYear} год отсутствуют`}
          />
        ) : (
          <>
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-8">
          <div className="glass-card">
            <CardHeader>
              <CardTitle className="text-sm text-dashboard-text-muted">Текущее значение</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-3xl font-bold text-dashboard-text">{formatValue(Number(currentValue))}</div>
              <div className={`flex items-center text-sm mt-2 ${change >= 0 ? 'text-emerald-400' : 'text-red-400'}`}>
                {change >= 0 ? <TrendingUp className="w-4 h-4 mr-1" /> : <TrendingDown className="w-4 h-4 mr-1" />}
                {change >= 0 ? '+' : ''}{change.toFixed(1)}% за месяц
              </div>
            </CardContent>
          </div>

          <div className="glass-card">
            <CardHeader>
              <CardTitle className="text-sm text-dashboard-text-muted">Среднее за год</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-3xl font-bold text-dashboard-text">
                {formatValue(activeMetric?.body
                  ? activeMetric.body.yearlyAverage
                  : monthlyData.reduce((sum, item) => sum + getValue(item), 0) / monthlyData.length
                )}
              </div>
            </CardContent>
          </div>

          <div className="glass-card">
            <CardHeader>
              <CardTitle className="text-sm text-dashboard-text-muted">Максимум за год</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-3xl font-bold text-dashboard-text">
                {formatValue(activeMetric?.body
                  ? activeMetric.body.yearlyMax
                  : Math.max(...monthlyData.map(getValue))
                )}
              </div>
            </CardContent>
          </div>
        </div>

        <div className="glass-card mb-8">
          <CardHeader>
            <CardTitle className="text-dashboard-text">График по месяцам</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="h-[400px]">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={monthlyData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.1)" />
                  <XAxis dataKey="month" tick={{ fill: '#94A3B8' }} />
                  <YAxis tickFormatter={(value) => metric === 'inflation' ? `${value}%` : `₽${(value / 1000).toFixed(0)}k`} tick={{ fill: '#94A3B8' }} />
                  <Tooltip
                    formatter={(value) => [formatValue(Number(value)), config.title]}
                    labelFormatter={(label) => `${label} ${selectedYear}`}
                    contentStyle={{ backgroundColor: '#0B1929', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '0.75rem', color: '#d6e3fa' }}
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
        </div>

        {metric === 'inflation' && inflationMetric?.body?.categoryBreakdown && inflationMetric.body.categoryBreakdown.length > 0 && (
          <div className="glass-card mb-8">
            <CardHeader>
              <CardTitle className="text-dashboard-text">Корзина расходов</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {inflationMetric.body.categoryBreakdown.map((cat) => (
                  <div key={cat.categoryId} className="p-4 rounded-lg bg-white/5 border border-white/10 hover:border-white/20 transition-colors">
                    <div className="flex justify-between items-center mb-1">
                      <span className="font-medium text-dashboard-text">
                        {cat.emoji} {cat.categoryName}
                      </span>
                      <span className={`text-sm font-bold ${cat.changePercent > 0 ? 'text-red-400' : 'text-green-400'}`}>
                        {cat.changePercent > 0 ? '+' : ''}{cat.changePercent}%
                      </span>
                    </div>
                    <div className="text-xs text-dashboard-text-muted mb-1">
                      {`${cat.avgCurrent.toLocaleString('ru-RU')} ₽/мес → ${cat.avgPrevious.toLocaleString('ru-RU')} ₽/мес`}
                    </div>
                    <div className="text-xs" style={{ color: 'rgba(148, 163, 184, 0.6)' }}>
                      вклад: {cat.contribution > 0 ? '+' : ''}{cat.contribution}%
                    </div>
                  </div>
                ))}
              </div>
            </CardContent>
          </div>
        )}

        <div className="glass-card">
          <CardHeader>
            <CardTitle className="text-dashboard-text">Данные по месяцам</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {monthlyData.map((data, index) => (
                <div key={index} className="p-4 rounded-lg border border-white/10 hover:border-white/20 transition-colors">
                  <div className="flex justify-between items-center">
                    <span className="font-medium text-dashboard-text">{data.month} {selectedYear}</span>
                    <span className="text-lg font-bold text-dashboard-text">
                      {formatValue(getValue(data))}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </div>
          </>
        )}
      </div>
    </div>
  );
};

export default MetricDetails;

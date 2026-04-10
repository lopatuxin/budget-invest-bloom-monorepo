import { useNavigate, Link } from 'react-router-dom';
import {
  Banknote,
  LineChart,
  ShoppingCart,
  PiggyBank,
  TrendingUp,
  TrendingDown,
  Wallet,
} from 'lucide-react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  AreaChart,
  Area,
} from 'recharts';
import { useAuth } from '@/contexts/AuthContext';
import { useBudgetSummary } from '@/hooks/useBudgetSummary';
import { useExpenseMetric } from '@/hooks/useExpenseMetric';
import { useIncomeMetric } from '@/hooks/useIncomeMetric';

const formatCurrency = (value: number) => value.toLocaleString('ru-RU') + ' \u20BD';

const DONUT_COLORS = ['#10B981', '#3B82F6', '#F59E0B', '#8B5CF6', '#EC4899', '#06B6D4', '#F97316', '#14B8A6'];

// Loading skeleton
const Skeleton = ({ className = '' }: { className?: string }) => (
  <div className={`animate-pulse bg-white/10 rounded-xl ${className}`} />
);

const Index = () => {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  const now = new Date();
  const currentMonth = String(now.getMonth() + 1);
  const currentYear = String(now.getFullYear());

  const { data: summaryResponse, isLoading: summaryLoading } = useBudgetSummary(currentMonth, currentYear, isAuthenticated);
  const summary = summaryResponse?.body;

  const { data: incomeResponse, isLoading: incomeLoading } = useIncomeMetric(currentYear, isAuthenticated);
  const incomeMetric = incomeResponse?.body;

  const { data: expenseResponse, isLoading: expenseLoading } = useExpenseMetric(currentYear, isAuthenticated);
  const expenseMetric = expenseResponse?.body;

  // If not authenticated, show landing page
  if (!isAuthenticated) {
    return (
      <div className="min-h-screen">
        {/* Hero Section */}
        <div className="max-w-7xl mx-auto px-6 pt-24 pb-16 text-center">
          <h1 className="text-5xl md:text-6xl font-bold text-dashboard-text">
            <span className="text-emerald-400">Управляйте</span>{' '}
            финансами с умом
          </h1>
          <p className="text-xl text-dashboard-text-muted mb-10 max-w-2xl mx-auto mt-6">
            Контролируйте расходы, планируйте бюджет и отслеживайте инвестиции в одном приложении
          </p>
          <div className="flex gap-4 justify-center flex-wrap">
            <Link to="/register" className="bg-emerald-500 hover:bg-emerald-600 text-white px-8 py-3 rounded-xl font-semibold text-lg transition-colors">
              Начать бесплатно
            </Link>
            <Link to="/login" className="bg-white/[0.06] border border-white/[0.12] rounded-xl px-8 py-3 text-dashboard-text hover:bg-white/10 font-semibold text-lg transition-colors">
              Войти
            </Link>
          </div>
        </div>

        {/* Feature Cards */}
        <div className="max-w-7xl mx-auto px-6 pb-16">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {/* Budget card */}
            <div className="glass-card p-6 text-center group hover:scale-[1.02] transition-all duration-300">
              <div className="w-14 h-14 rounded-2xl bg-emerald-500/20 flex items-center justify-center mx-auto mb-4">
                <Wallet className="w-7 h-7 text-emerald-400" />
              </div>
              <h3 className="text-lg font-semibold text-dashboard-text mb-2">Контроль бюджета</h3>
              <p className="text-sm text-dashboard-text-muted">Отслеживайте доходы и расходы по категориям с наглядной аналитикой</p>
            </div>
            {/* Investments card */}
            <div className="glass-card p-6 text-center group hover:scale-[1.02] transition-all duration-300">
              <div className="w-14 h-14 rounded-2xl bg-blue-500/20 flex items-center justify-center mx-auto mb-4">
                <TrendingUp className="w-7 h-7 text-blue-400" />
              </div>
              <h3 className="text-lg font-semibold text-dashboard-text mb-2">Инвестиции</h3>
              <p className="text-sm text-dashboard-text-muted">Управляйте портфелем и отслеживайте доходность активов</p>
            </div>
            {/* Savings card */}
            <div className="glass-card p-6 text-center group hover:scale-[1.02] transition-all duration-300">
              <div className="w-14 h-14 rounded-2xl bg-purple-500/20 flex items-center justify-center mx-auto mb-4">
                <PiggyBank className="w-7 h-7 text-purple-400" />
              </div>
              <h3 className="text-lg font-semibold text-dashboard-text mb-2">Сбережения</h3>
              <p className="text-sm text-dashboard-text-muted">Планируйте и достигайте финансовых целей</p>
            </div>
          </div>
        </div>
      </div>
    );
  }

  // Build chart data for bar chart (last 6 months)
  const barChartData = (() => {
    if (!incomeMetric?.monthlyData && !expenseMetric?.monthlyData) return [];
    const incomeMap = new Map((incomeMetric?.monthlyData || []).map(d => [d.month, d]));
    const expenseMap = new Map((expenseMetric?.monthlyData || []).map(d => [d.month, d]));
    const allMonths = new Set([
      ...(incomeMetric?.monthlyData || []).map(d => d.month),
      ...(expenseMetric?.monthlyData || []).map(d => d.month),
    ]);
    return Array.from(allMonths)
      .sort((a, b) => a - b)
      .slice(-6)
      .map(month => ({
        name: incomeMap.get(month)?.monthName || expenseMap.get(month)?.monthName || String(month),
        income: incomeMap.get(month)?.amount || 0,
        expenses: expenseMap.get(month)?.amount || 0,
      }));
  })();

  // Donut data from categories
  const donutData = (summary?.categories || []).map((cat, i) => ({
    name: `${cat.emoji} ${cat.name}`,
    value: cat.amount,
    color: DONUT_COLORS[i % DONUT_COLORS.length],
  }));
  const totalCategoryAmount = donutData.reduce((sum, d) => sum + d.value, 0);

  // Area chart data for income trend
  const incomeAreaData = (incomeMetric?.monthlyData || []).slice(-6).map(d => ({
    name: d.monthName,
    amount: d.amount,
  }));

  const isChartsLoading = incomeLoading || expenseLoading;

  // KPI cards config
  const kpiCards = [
    {
      label: 'БАЛАНС СЧЕТА',
      value: summary ? formatCurrency(summary.balance) : '--',
      trend: summary?.trends?.balance,
      icon: Banknote,
      color: '#10B981',
      glow: 'rgba(16, 185, 129, 0.3)',
    },
    {
      label: 'ИНВЕСТИЦИИ',
      value: '\u2014',
      trend: null,
      icon: LineChart,
      color: '#3B82F6',
      glow: 'rgba(59, 130, 246, 0.3)',
    },
    {
      label: 'РАСХОДЫ МЕС.',
      value: summary ? formatCurrency(summary.expenses) : '--',
      trend: summary?.trends?.expenses,
      icon: ShoppingCart,
      color: '#F59E0B',
      glow: 'rgba(245, 158, 11, 0.3)',
    },
    {
      label: 'СБЕРЕЖЕНИЯ',
      value: summary ? formatCurrency(summary.capital) : '--',
      trend: summary?.trends?.capital,
      icon: PiggyBank,
      color: '#8B5CF6',
      glow: 'rgba(139, 92, 246, 0.3)',
    },
  ];

  const parseTrend = (trend: string | null | undefined) => {
    if (!trend) return null;
    const num = parseFloat(trend);
    if (isNaN(num)) return null;
    return { value: trend, isPositive: num >= 0 };
  };

  return (
    <div className="space-y-6 pb-6">
      {/* KPI Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-5">
        {summaryLoading
          ? Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-[130px]" />)
          : kpiCards.map(card => {
              const Icon = card.icon;
              const trend = parseTrend(card.trend);
              return (
                <div
                  key={card.label}
                  className="glass-card p-5 flex items-start justify-between group transition-all duration-300 hover:scale-[1.02]"
                >
                  <div className="space-y-2">
                    <p className="text-[11px] font-semibold tracking-widest text-dashboard-text-muted">
                      {card.label}
                    </p>
                    <p className="text-2xl font-bold text-dashboard-text font-mono">{card.value}</p>
                    {trend && (
                      <div className={`flex items-center gap-1 text-xs font-medium ${trend.isPositive ? 'text-emerald-400' : 'text-red-400'}`}>
                        {trend.isPositive ? <TrendingUp className="w-3.5 h-3.5" /> : <TrendingDown className="w-3.5 h-3.5" />}
                        <span className="font-mono">{trend.value}</span>
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

      {/* Charts Row */}
      <div className="grid grid-cols-1 lg:grid-cols-5 gap-5">
        {/* Bar Chart */}
        <div className="lg:col-span-3 glass-card p-5">
          <h3 className="text-sm font-semibold text-dashboard-text mb-4">Динамика расходов и доходов</h3>
          {isChartsLoading ? (
            <Skeleton className="h-[280px]" />
          ) : (
            <ResponsiveContainer width="100%" height={280}>
              <BarChart data={barChartData} barGap={4}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.06)" />
                <XAxis dataKey="name" tick={{ fill: '#94A3B8', fontSize: 12 }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fill: '#94A3B8', fontSize: 12 }} axisLine={false} tickLine={false} tickFormatter={v => `${(v / 1000).toFixed(0)}k`} />
                <Tooltip
                  contentStyle={{ background: '#0B1929', border: '1px solid rgba(255,255,255,0.12)', borderRadius: 12, color: '#d6e3fa' }}
                  formatter={(value: number) => formatCurrency(value)}
                />
                <Legend wrapperStyle={{ color: '#94A3B8', fontSize: 12 }} />
                <Bar dataKey="income" name="Доходы" fill="#10B981" radius={[6, 6, 0, 0]} maxBarSize={32} />
                <Bar dataKey="expenses" name="Расходы" fill="#F59E0B" radius={[6, 6, 0, 0]} maxBarSize={32} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>

        {/* Donut Chart */}
        <div className="lg:col-span-2 glass-card p-5">
          <h3 className="text-sm font-semibold text-dashboard-text mb-4">Распределение по категориям</h3>
          {summaryLoading ? (
            <Skeleton className="h-[280px]" />
          ) : donutData.length === 0 ? (
            <div className="h-[280px] flex items-center justify-center text-dashboard-text-muted text-sm">
              Нет данных по категориям
            </div>
          ) : (
            <div>
              <ResponsiveContainer width="100%" height={200}>
                <PieChart>
                  <Pie
                    data={donutData}
                    cx="50%"
                    cy="50%"
                    innerRadius={55}
                    outerRadius={85}
                    paddingAngle={3}
                    dataKey="value"
                    stroke="none"
                  >
                    {donutData.map((entry, index) => (
                      <Cell key={index} fill={entry.color} />
                    ))}
                  </Pie>
                  <Tooltip
                    contentStyle={{ background: '#0B1929', border: '1px solid rgba(255,255,255,0.12)', borderRadius: 12, color: '#d6e3fa' }}
                    formatter={(value: number) => formatCurrency(value)}
                  />
                </PieChart>
              </ResponsiveContainer>
              {/* Center total */}
              <div className="text-center -mt-[130px] mb-[90px]">
                <p className="text-xs text-dashboard-text-muted">Всего</p>
                <p className="text-lg font-bold text-dashboard-text font-mono">{formatCurrency(totalCategoryAmount)}</p>
              </div>
              {/* Legend */}
              <div className="grid grid-cols-2 gap-x-4 gap-y-1.5 mt-2">
                {donutData.slice(0, 6).map((item, i) => (
                  <div key={i} className="flex items-center gap-2 text-xs text-dashboard-text-muted truncate">
                    <div className="w-2.5 h-2.5 rounded-full shrink-0" style={{ backgroundColor: item.color }} />
                    <span className="truncate">{item.name}</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Bottom Row */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
        {/* Category List */}
        <div className="lg:col-span-7 glass-card p-5">
          <h3 className="text-sm font-semibold text-dashboard-text mb-4">Основные категории трат</h3>
          {summaryLoading ? (
            <div className="space-y-3">
              {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-14" />)}
            </div>
          ) : !summary?.categories?.length ? (
            <div className="h-[200px] flex items-center justify-center text-dashboard-text-muted text-sm">
              Нет данных по категориям
            </div>
          ) : (
            <div className="space-y-2 max-h-[300px] overflow-y-auto dashboard-scroll pr-1">
              {summary.categories.map((cat, i) => (
                <button
                  key={cat.id}
                  onClick={() => navigate(`/budget/category/${cat.id}`)}
                  className="w-full flex items-center gap-4 px-4 py-3 rounded-xl bg-white/[0.03] hover:bg-white/[0.07] transition-all duration-200 text-left group"
                >
                  <div
                    className="w-10 h-10 rounded-xl flex items-center justify-center text-lg shrink-0"
                    style={{ backgroundColor: `${DONUT_COLORS[i % DONUT_COLORS.length]}20` }}
                  >
                    {cat.emoji}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-dashboard-text truncate">{cat.name}</p>
                    <div className="w-full bg-white/5 rounded-full h-1.5 mt-1.5">
                      <div
                        className="h-1.5 rounded-full transition-all duration-500"
                        style={{
                          width: `${Math.min(cat.percentUsed, 100)}%`,
                          backgroundColor: DONUT_COLORS[i % DONUT_COLORS.length],
                        }}
                      />
                    </div>
                  </div>
                  <div className="text-right shrink-0">
                    <p className="text-sm font-semibold text-dashboard-text font-mono">{formatCurrency(cat.amount)}</p>
                    <p className="text-xs text-dashboard-text-muted font-mono">{cat.percentUsed}%</p>
                  </div>
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Income Trend */}
        <div className="lg:col-span-5 glass-card p-5">
          <h3 className="text-sm font-semibold text-dashboard-text mb-4">Тренд доходов</h3>
          {incomeLoading ? (
            <Skeleton className="h-[220px]" />
          ) : incomeAreaData.length === 0 ? (
            <div className="h-[220px] flex items-center justify-center text-dashboard-text-muted text-sm">
              Нет данных по доходам
            </div>
          ) : (
            <>
              <ResponsiveContainer width="100%" height={200}>
                <AreaChart data={incomeAreaData}>
                  <defs>
                    <linearGradient id="incomeGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#10B981" stopOpacity={0.3} />
                      <stop offset="100%" stopColor="#10B981" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.06)" />
                  <XAxis dataKey="name" tick={{ fill: '#94A3B8', fontSize: 12 }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fill: '#94A3B8', fontSize: 12 }} axisLine={false} tickLine={false} tickFormatter={v => `${(v / 1000).toFixed(0)}k`} />
                  <Tooltip
                    contentStyle={{ background: '#0B1929', border: '1px solid rgba(255,255,255,0.12)', borderRadius: 12, color: '#d6e3fa' }}
                    formatter={(value: number) => formatCurrency(value)}
                  />
                  <Area type="monotone" dataKey="amount" name="Доход" stroke="#10B981" strokeWidth={2} fill="url(#incomeGrad)" />
                </AreaChart>
              </ResponsiveContainer>
              {incomeMetric && (
                <div className="flex gap-6 mt-3 pt-3 border-t border-white/8">
                  <div>
                    <p className="text-[11px] text-dashboard-text-muted tracking-wide">Средний доход</p>
                    <p className="text-sm font-semibold text-dashboard-text font-mono">{formatCurrency(incomeMetric.yearlyAverage)}</p>
                  </div>
                  <div>
                    <p className="text-[11px] text-dashboard-text-muted tracking-wide">Максимум</p>
                    <p className="text-sm font-semibold text-dashboard-text font-mono">{formatCurrency(incomeMetric.yearlyMax)}</p>
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default Index;

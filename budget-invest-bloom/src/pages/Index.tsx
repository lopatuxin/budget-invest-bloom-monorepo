import { Button } from '@/components/ui/button';
import { ArrowRight, TrendingUp, PieChart, Target } from 'lucide-react';
import { Link } from 'react-router-dom';
import FinanceCard from '@/components/FinanceCard';

const Index = () => {
  // Примерные данные для дашборда
  const totalBalance = 2450000;
  const monthlyIncome = 150000;
  const monthlyExpenses = 89500;
  const investmentValue = 850000;

  return (
    <div className="min-h-screen bg-gradient-background">
      <div className="max-w-7xl mx-auto p-6">
        {/* Hero секция */}
        <div className="text-center mb-12 pt-8">
          <h1 className="text-5xl font-bold mb-4">
            <span className="bg-gradient-primary bg-clip-text text-transparent">
              Управляйте
            </span>{' '}
            <span className="text-foreground">финансами</span>
          </h1>
          <p className="text-xl text-muted-foreground mb-8 max-w-2xl mx-auto">
            Контролируйте расходы, планируйте бюджет и отслеживайте инвестиции в одном приложении
          </p>
          <div className="flex gap-4 justify-center">
            <Link to="/budget">
              <Button size="lg" className="bg-gradient-primary hover:opacity-90">
                Начать управление <ArrowRight className="ml-2 h-4 w-4" />
              </Button>
            </Link>
            <Link to="/investments">
              <Button variant="outline" size="lg">
                Мои инвестиции
              </Button>
            </Link>
          </div>
        </div>

        {/* Карточки обзора */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-12">
          <FinanceCard
            title="Общий баланс"
            value={`₽${totalBalance.toLocaleString()}`}
            icon={<PieChart className="w-5 h-5" />}
            trend={{ value: "+12.5%", isPositive: true }}
            gradient="primary"
          />
          <FinanceCard
            title="Доходы за месяц"
            value={`₽${monthlyIncome.toLocaleString()}`}
            icon={<TrendingUp className="w-5 h-5" />}
            trend={{ value: "+8.2%", isPositive: true }}
            gradient="success"
          />
          <FinanceCard
            title="Расходы за месяц"
            value={`₽${monthlyExpenses.toLocaleString()}`}
            icon={<Target className="w-5 h-5" />}
            trend={{ value: "+3.1%", isPositive: false }}
            gradient="secondary"
          />
          <FinanceCard
            title="Инвестиции"
            value={`₽${investmentValue.toLocaleString()}`}
            icon={<TrendingUp className="w-5 h-5" />}
            trend={{ value: "+15.6%", isPositive: true }}
            gradient="primary"
          />
        </div>

        {/* Призыв к действию */}
        <div className="bg-card rounded-2xl p-8 shadow-card border-0 text-center">
          <h2 className="text-2xl font-bold mb-4 text-foreground">
            Готовы взять финансы под контроль?
          </h2>
          <p className="text-muted-foreground mb-6 max-w-lg mx-auto">
            Начните с создания бюджета или добавьте первые инвестиции в ваш портфель
          </p>
          <div className="flex gap-4 justify-center">
            <Link to="/budget">
              <Button className="bg-gradient-success hover:opacity-90">
                Создать бюджет
              </Button>
            </Link>
            <Link to="/investments">
              <Button className="bg-gradient-secondary hover:opacity-90">
                Добавить инвестиции
              </Button>
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Index;

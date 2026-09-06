import { Link } from 'react-router-dom';
import { Wallet, TrendingUp, PiggyBank } from 'lucide-react';
import { useAuth } from '@/contexts/AuthContext';
import { OverviewPage } from '@/pages/overview/OverviewPage';

const Index = () => {
  const { isAuthenticated } = useAuth();

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
            <Link to="/register" className="btn-cta px-8 py-3 rounded-xl text-lg">
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

  return <OverviewPage />;
};

export default Index;

import { Link, useLocation } from 'react-router-dom';
import { LayoutDashboard, Wallet, TrendingUp, BarChart3, Settings, LogOut, User } from 'lucide-react';
import { useAuth } from '@/contexts/AuthContext';
import { cn } from '@/lib/utils';
import { toast } from '@/hooks/use-toast';

const navItems = [
  { href: '/', label: 'Обзор', icon: LayoutDashboard },
  { href: '/budget', label: 'Бюджет', icon: Wallet },
  { href: '/investments', label: 'Инвестиции', icon: TrendingUp },
  { href: '/budget/metric/expenses', label: 'Аналитика', icon: BarChart3 },
];

const Sidebar = () => {
  const location = useLocation();
  const { user, logout } = useAuth();

  const isActive = (href: string) => {
    if (href === '/') return location.pathname === '/';
    return location.pathname.startsWith(href);
  };

  const handleLogout = async () => {
    try {
      await logout();
    } catch (error) {
      console.error('Logout error:', error);
      toast({
        title: 'Ошибка выхода',
        description: error instanceof Error ? error.message : 'Не удалось выполнить выход.',
        variant: 'destructive',
      });
    }
  };

  const displayName = user?.name || (user?.firstName && user?.lastName ? `${user.firstName} ${user.lastName}` : user?.email) || 'Пользователь';

  return (
    <aside
      className="fixed left-4 top-4 bottom-4 w-[240px] z-50 flex flex-col rounded-2xl border border-white/10"
      style={{ background: 'linear-gradient(to bottom, #0F3547, #0A2A3D)' }}
    >
      {/* Logo */}
      <div className="px-5 pt-6 pb-4">
        <Link to="/" className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-emerald-500/20 flex items-center justify-center">
            <Wallet className="w-5 h-5 text-emerald-400" />
          </div>
          <span className="text-lg font-bold text-emerald-400 tracking-tight">Мои финансы</span>
        </Link>
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-3 mt-4 space-y-1">
        {navItems.map(({ href, label, icon: Icon }) => {
          const active = isActive(href);
          return (
            <Link
              key={href}
              to={href}
              className={cn(
                'flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-all duration-200 relative',
                active
                  ? 'text-emerald-400 bg-emerald-500/10'
                  : 'text-dashboard-text-muted hover:text-dashboard-text hover:bg-white/5'
              )}
            >
              {active && (
                <div className="absolute left-0 top-1/2 -translate-y-1/2 w-[3px] h-5 bg-emerald-400 rounded-r-full" />
              )}
              <Icon className="w-[18px] h-[18px] shrink-0" />
              <span>{label}</span>
            </Link>
          );
        })}
      </nav>

      {/* Bottom section */}
      <div className="px-3 pb-5 space-y-1 border-t border-white/8 pt-3 mt-2">
        {/* User info */}
        <div className="flex items-center gap-3 px-3 py-2.5">
          <div className="w-8 h-8 rounded-full bg-white/10 flex items-center justify-center">
            <User className="w-4 h-4 text-dashboard-text-muted" />
          </div>
          <span className="text-sm text-dashboard-text truncate">{displayName}</span>
        </div>

        <Link
          to="/settings"
          className="flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm text-dashboard-text-muted hover:text-dashboard-text hover:bg-white/5 transition-all duration-200"
        >
          <Settings className="w-[18px] h-[18px]" />
          <span>Настройки</span>
        </Link>

        <button
          onClick={handleLogout}
          className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm text-dashboard-text-muted hover:text-red-400 hover:bg-red-500/10 transition-all duration-200"
        >
          <LogOut className="w-[18px] h-[18px]" />
          <span>Выйти</span>
        </button>
      </div>
    </aside>
  );
};

export default Sidebar;

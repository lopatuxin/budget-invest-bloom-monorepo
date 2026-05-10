import { useMemo } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { LogOut, User, CirclePlus, FolderPlus } from 'lucide-react';
import { useAuth } from '@/contexts/AuthContext';
import { cn } from '@/lib/utils';
import { toast } from '@/hooks/use-toast';
import { NAV_ITEMS, isNavItemActive } from '@/lib/nav';

const Sidebar = () => {
  const location = useLocation();
  const { user, logout } = useAuth();

  // Compute active index once to avoid O(N²) per render
  const activeIndex = useMemo(
    () => NAV_ITEMS.findIndex(item => isNavItemActive(item, location.pathname)),
    [location.pathname]
  );

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
      aria-label="Основная навигация"
      className="fixed left-4 top-4 bottom-4 w-[240px] z-50 hidden lg:flex flex-col rounded-2xl border border-white/10"
      style={{ background: 'linear-gradient(to bottom, #0F3547, #0A2A3D)' }}
    >
      {/* Navigation */}
      <nav className="flex-1 px-3 mt-4 space-y-1">
        {NAV_ITEMS.map((item, idx) => {
          const { href, label, icon: Icon, linkTo } = item;
          const active = idx === activeIndex;
          // Keep current path for active metric pages to avoid resetting the selected metric
          const targetTo = active && location.pathname.startsWith(href + '/') ? location.pathname : linkTo;
          return (
            <Link
              key={href}
              to={targetTo}
              aria-current={active ? 'page' : undefined}
              className={cn(
                'flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-all duration-200 relative',
                active
                  ? 'text-emerald-400 bg-emerald-500/10 shadow-[0_0_12px_rgba(16,185,129,0.15)]'
                  : 'text-dashboard-text-muted hover:text-dashboard-text hover:bg-white/5'
              )}
            >
              {active && (
                <div className="absolute left-0 top-1/2 -translate-y-1/2 w-[3.5px] h-6 bg-emerald-400 rounded-r-full shadow-[0_0_6px_rgba(16,185,129,0.4)]" />
              )}
              <Icon aria-hidden="true" className="w-[18px] h-[18px] shrink-0" />
              <span>{label}</span>
            </Link>
          );
        })}
      </nav>

      {/* Quick actions */}
      <div className="px-3 mt-1 mb-2 space-y-1.5 border-t border-white/8 pt-3">
        <p className="px-3 text-[11px] font-medium uppercase tracking-wider text-dashboard-text-muted/60 mb-1">
          Быстрые действия
        </p>
        <Link
          to="/budget?action=expense"
          className="flex items-center gap-2.5 px-3 py-2 rounded-xl text-sm font-medium bg-amber-500/10 text-amber-400 hover:bg-amber-500/20 transition-all duration-200"
        >
          <CirclePlus aria-hidden="true" className="w-4 h-4 shrink-0" />
          <span>Добавить расход</span>
        </Link>
        <Link
          to="/budget?action=income"
          className="flex items-center gap-2.5 px-3 py-2 rounded-xl text-sm font-medium bg-emerald-500/10 text-emerald-400 hover:bg-emerald-500/20 transition-all duration-200"
        >
          <CirclePlus aria-hidden="true" className="w-4 h-4 shrink-0" />
          <span>Добавить доход</span>
        </Link>
        <Link
          to="/budget?action=category"
          className="flex items-center gap-2.5 px-3 py-2 rounded-xl text-sm font-medium bg-blue-500/10 text-blue-400 hover:bg-blue-500/20 transition-all duration-200"
        >
          <FolderPlus aria-hidden="true" className="w-4 h-4 shrink-0" />
          <span>Добавить категорию</span>
        </Link>
        <Link
          to="/investments?action=add-asset"
          className="flex items-center gap-2.5 px-3 py-2 rounded-xl text-sm font-medium bg-violet-500/10 text-violet-400 hover:bg-violet-500/20 transition-all duration-200"
        >
          <CirclePlus aria-hidden="true" className="w-4 h-4 shrink-0" />
          <span>Добавить актив</span>
        </Link>
      </div>

      {/* Bottom section */}
      <div className="px-3 pb-5 space-y-1 border-t border-white/8 pt-3 mt-2">
        {/* User info */}
        <div className="flex items-center gap-3 px-3 py-2.5">
          <div className="w-8 h-8 rounded-full bg-white/10 flex items-center justify-center">
            <User aria-hidden="true" className="w-4 h-4 text-dashboard-text-muted" />
          </div>
          <span className="text-sm text-dashboard-text truncate" title={displayName}>{displayName}</span>
        </div>

        <button
          onClick={handleLogout}
          className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm text-dashboard-text-muted hover:text-red-400 hover:bg-red-500/10 transition-all duration-200"
        >
          <LogOut aria-hidden="true" className="w-[18px] h-[18px]" />
          <span>Выйти</span>
        </button>
      </div>
    </aside>
  );
};

export default Sidebar;

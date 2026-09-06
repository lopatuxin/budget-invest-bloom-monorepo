import { useMemo } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { LogOut, Plus, Sprout } from 'lucide-react';
import { useAuth } from '@/contexts/AuthContext';
import { useOperationDialog } from '@/components/operation/OperationDialogProvider';
import { cn } from '@/lib/utils';
import { toast } from '@/hooks/use-toast';
import { NAV_ITEMS, isNavItemActive } from '@/lib/nav';

interface RailUser {
  name?: string;
  firstName?: string;
  lastName?: string;
  email: string;
}

function getInitials(user: RailUser | null): string {
  if (!user) return '?';
  const displayName = user.name || (user.firstName && user.lastName ? `${user.firstName} ${user.lastName}` : '');
  const words = displayName.trim().split(/\s+/).filter(Boolean);
  if (words.length >= 2) return (words[0][0] + words[1][0]).toUpperCase();
  if (words.length === 1) return words[0][0].toUpperCase();
  return user.email[0]?.toUpperCase() ?? '?';
}

// Narrow 76px navigation rail — replaces the old wide Sidebar for authenticated pages.
const AppRail = () => {
  const location = useLocation();
  const { user, logout } = useAuth();
  const { openOperationDialog } = useOperationDialog();

  const activeIndex = useMemo(
    () => NAV_ITEMS.findIndex((item) => isNavItemActive(item, location.pathname)),
    [location.pathname]
  );

  const handleLogout = async () => {
    try {
      await logout();
    } catch (error) {
      toast({
        title: 'Ошибка выхода',
        description: error instanceof Error ? error.message : 'Не удалось выполнить выход.',
        variant: 'destructive',
      });
    }
  };

  const displayName = user?.name || (user?.firstName && user?.lastName ? `${user.firstName} ${user.lastName}` : user?.email);

  return (
    <aside
      aria-label="Основная навигация"
      className="fixed left-0 top-0 bottom-0 z-40 hidden lg:flex flex-col w-[76px] bg-app-surface border-r border-app-border"
    >
      <div className="flex items-center justify-center pt-5 pb-4">
        <div className="w-[34px] h-[34px] rounded-lg bg-app-accent flex items-center justify-center">
          <Sprout aria-hidden="true" className="w-[18px] h-[18px] text-app-accent-ink" />
        </div>
      </div>

      <nav className="flex flex-col items-center gap-1 mt-1">
        {NAV_ITEMS.map((item, idx) => {
          const { href, label, icon: Icon, linkTo } = item;
          const active = idx === activeIndex;
          const targetTo = active && location.pathname.startsWith(href + '/') ? location.pathname : linkTo;
          return (
            <Link
              key={href}
              to={targetTo}
              aria-current={active ? 'page' : undefined}
              className={cn(
                'flex flex-col items-center justify-center gap-1 w-[60px] h-[54px] rounded-lg transition-colors',
                active
                  ? 'bg-app-accent-soft text-app-accent'
                  : 'text-app-text-muted hover:text-app-text hover:bg-app-surface-2'
              )}
            >
              <Icon aria-hidden="true" className="w-5 h-5" />
              <span className="text-[10px] leading-none font-medium">{label}</span>
            </Link>
          );
        })}
      </nav>

      <div className="w-8 mx-auto my-4 border-t border-app-border" />

      <div className="flex flex-col items-center gap-3">
        <div className="flex flex-col items-center gap-1">
          <button
            type="button"
            aria-label="Расход"
            onClick={() => openOperationDialog('expense')}
            className="w-10 h-10 rounded-full bg-app-accent text-app-accent-ink flex items-center justify-center hover:opacity-90 transition-opacity"
          >
            <Plus aria-hidden="true" className="w-5 h-5" />
          </button>
          <span className="text-[10px] leading-none text-app-text-muted">Расход</span>
        </div>
        <div className="flex flex-col items-center gap-1">
          <button
            type="button"
            aria-label="Доход"
            onClick={() => openOperationDialog('income')}
            className="w-10 h-10 rounded-full border-[1.5px] border-app-accent text-app-accent flex items-center justify-center hover:bg-app-accent-soft transition-colors"
          >
            <Plus aria-hidden="true" className="w-5 h-5" />
          </button>
          <span className="text-[10px] leading-none text-app-text-muted">Доход</span>
        </div>
      </div>

      <div className="flex-1" />

      <div className="flex flex-col items-center gap-2 pb-5">
        <div
          className="w-[34px] h-[34px] rounded-full bg-app-surface-2 border border-app-border flex items-center justify-center text-[12px] font-semibold text-app-text"
          title={displayName}
        >
          {getInitials(user)}
        </div>
        <button
          type="button"
          aria-label="Выйти"
          title="Выйти"
          onClick={handleLogout}
          className="w-8 h-8 rounded-lg flex items-center justify-center text-app-text-muted hover:text-app-bad hover:bg-app-bad-soft transition-colors"
        >
          <LogOut aria-hidden="true" className="w-[18px] h-[18px]" />
        </button>
      </div>
    </aside>
  );
};

export default AppRail;

import { useMemo } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Plus } from 'lucide-react';
import { NAV_ITEMS, isNavItemActive, type NavItem } from '@/lib/nav';
import { useOperationDialog } from '@/components/operation/OperationDialogProvider';

// Mobile bottom navigation: Обзор, Бюджет, "+", Инвестиции, Аналитика.
const BottomNav = () => {
  const location = useLocation();
  const { openOperationDialog } = useOperationDialog();

  const activeIndex = useMemo(
    () => NAV_ITEMS.findIndex((item) => isNavItemActive(item, location.pathname)),
    [location.pathname]
  );

  const renderItem = (item: NavItem, idx: number) => {
    const { href, label, icon: Icon, linkTo } = item;
    const active = idx === activeIndex;
    const targetTo = active && location.pathname.startsWith(href + '/') ? location.pathname : linkTo;
    return (
      <Link
        key={href}
        to={targetTo}
        aria-current={active ? 'page' : undefined}
        className="flex flex-1 flex-col items-center justify-center gap-0.5 min-w-[44px] min-h-[44px]"
      >
        <Icon aria-hidden="true" className={`w-5 h-5 ${active ? 'text-app-accent' : 'text-app-text-muted'}`} />
        <span className={`text-[11px] leading-none ${active ? 'text-app-accent font-medium' : 'text-app-text-muted'}`}>
          {label}
        </span>
      </Link>
    );
  };

  return (
    <nav
      aria-label="Мобильная навигация"
      className="fixed bottom-0 left-0 right-0 z-40 lg:hidden flex items-stretch h-16 bg-app-surface border-t border-app-border"
      style={{ paddingBottom: 'env(safe-area-inset-bottom, 0px)' }}
    >
      {NAV_ITEMS.map((item, idx) => (
        <div key={item.href} className="contents">
          {renderItem(item, idx)}
          {idx === 1 && (
            <div className="flex flex-1 items-center justify-center relative">
              <button
                type="button"
                aria-label="Записать операцию"
                onClick={() => openOperationDialog('expense')}
                className="absolute -top-6 w-14 h-14 rounded-full bg-app-accent text-app-accent-ink flex items-center justify-center"
                style={{ border: '5px solid rgb(var(--app-bg))' }}
              >
                <Plus aria-hidden="true" className="w-6 h-6" />
              </button>
            </div>
          )}
        </div>
      ))}
    </nav>
  );
};

export default BottomNav;

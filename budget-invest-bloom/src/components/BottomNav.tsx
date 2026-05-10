import { useMemo } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { NAV_ITEMS, isNavItemActive } from '@/lib/nav';

const BottomNav = () => {
  const location = useLocation();

  // Compute active index once to avoid O(N²) per render
  const activeIndex = useMemo(
    () => NAV_ITEMS.findIndex(item => isNavItemActive(item, location.pathname)),
    [location.pathname]
  );

  return (
    <nav
      aria-label="Мобильная навигация"
      className="fixed bottom-0 left-0 right-0 z-50 lg:hidden flex items-center justify-around"
      style={{
        background: 'rgba(11, 25, 41, 0.95)',
        backdropFilter: 'blur(12px)',
        WebkitBackdropFilter: 'blur(12px)',
        borderTop: '1px solid rgba(255,255,255,0.1)',
        paddingBottom: 'env(safe-area-inset-bottom, 0px)',
      }}
    >
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
            className="flex flex-col items-center justify-center min-w-[44px] min-h-[44px] px-3"
          >
            <Icon
              aria-hidden="true"
              className={`w-5 h-5 ${active ? 'text-emerald-400' : 'text-dashboard-text-muted'}`}
            />
            {/* Active dot indicator */}
            {active && (
              <div className="w-[5px] h-[2px] rounded-full bg-emerald-400 mt-1" />
            )}
            <span
              className={`text-xs leading-tight mt-0.5 ${
                active ? 'text-emerald-400 font-medium' : 'text-dashboard-text-muted'
              }`}
            >
              {label}
            </span>
          </Link>
        );
      })}
    </nav>
  );
};

export default BottomNav;

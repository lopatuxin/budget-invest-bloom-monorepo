import { Link, useLocation } from 'react-router-dom';
import { LayoutDashboard, Wallet, TrendingUp, BarChart3 } from 'lucide-react';

const navItems = [
  { href: '/', label: 'Обзор', icon: LayoutDashboard },
  { href: '/budget', label: 'Бюджет', icon: Wallet },
  { href: '/investments', label: 'Инвестиции', icon: TrendingUp },
  { href: '/budget/metric', label: 'Аналитика', icon: BarChart3, linkTo: '/budget/metric/expenses' },
];

const BottomNav = () => {
  const location = useLocation();

  const isActive = (href: string) => {
    if (href === '/') return location.pathname === '/';
    const matches = location.pathname === href || location.pathname.startsWith(href + '/');
    if (!matches) return false;
    // Prefer the most specific matching nav item
    return !navItems.some(item => item.href !== href && item.href.startsWith(href + '/') && (location.pathname === item.href || location.pathname.startsWith(item.href + '/')));
  };

  return (
    <nav
      className="fixed bottom-0 left-0 right-0 z-50 lg:hidden flex items-center justify-around"
      style={{
        background: 'rgba(11, 25, 41, 0.95)',
        backdropFilter: 'blur(12px)',
        WebkitBackdropFilter: 'blur(12px)',
        borderTop: '1px solid rgba(255,255,255,0.1)',
        paddingBottom: 'env(safe-area-inset-bottom, 0px)',
      }}
    >
      {navItems.map(({ href, label, icon: Icon, linkTo }) => {
        const active = isActive(href);
        return (
          <Link
            key={href}
            to={linkTo || href}
            aria-current={active ? 'page' : undefined}
            className="flex flex-col items-center justify-center min-w-[44px] min-h-[44px] px-3"
          >
            <Icon
              className={`w-5 h-5 ${active ? 'text-emerald-400' : 'text-dashboard-text-muted'}`}
            />
            {/* Active dot indicator */}
            {active && (
              <div className="w-[5px] h-[2px] rounded-full bg-emerald-400 mt-1" />
            )}
            <span
              className={`text-[10px] leading-tight mt-0.5 ${
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

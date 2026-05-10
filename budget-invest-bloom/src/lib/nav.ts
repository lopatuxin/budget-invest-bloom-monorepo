import { LayoutDashboard, Wallet, TrendingUp, BarChart3, type LucideIcon } from 'lucide-react';

export interface NavItem {
  label: string;
  icon: LucideIcon;
  href: string;
  linkTo: string;
}

export const NAV_ITEMS: NavItem[] = [
  { href: '/', label: 'Обзор', icon: LayoutDashboard, linkTo: '/' },
  { href: '/budget', label: 'Бюджет', icon: Wallet, linkTo: '/budget' },
  { href: '/investments', label: 'Инвестиции', icon: TrendingUp, linkTo: '/investments' },
  { href: '/budget/metric', label: 'Аналитика', icon: BarChart3, linkTo: '/budget/metric/expenses' },
];

/**
 * Returns true if the nav item is the most-specific match for the current pathname.
 * Prefers the deepest matching href over a shallower one.
 */
export function isNavItemActive(item: NavItem, pathname: string): boolean {
  if (item.href === '/') return pathname === '/';
  const matches = pathname === item.href || pathname.startsWith(item.href + '/');
  if (!matches) return false;
  // Prefer the most specific matching nav item
  return !NAV_ITEMS.some(
    other =>
      other.href !== item.href &&
      other.href.startsWith(item.href + '/') &&
      (pathname === other.href || pathname.startsWith(other.href + '/'))
  );
}

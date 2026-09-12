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
  { href: '/analytics', label: 'Аналитика', icon: BarChart3, linkTo: '/analytics' },
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

// Nested routes under here are drill-down detail pages, not tabs of their section —
// clicking the section's nav item should return to the section, not preserve the path.
const DRILL_DOWN_PATH_PREFIXES = ['/budget/category/'];

/**
 * Link target for a nav item: a still-active nested route (e.g. /analytics/:tab) keeps the
 * current path so switching sections doesn't lose the open tab, except for drill-down pages.
 */
export function navItemTargetTo(item: NavItem, pathname: string, isActive: boolean): string {
  const isDrillDown = DRILL_DOWN_PATH_PREFIXES.some(prefix => pathname.startsWith(prefix));
  if (isActive && !isDrillDown && pathname.startsWith(item.href + '/')) {
    return pathname;
  }
  return item.linkTo;
}

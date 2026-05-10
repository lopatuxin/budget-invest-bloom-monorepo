import { useState, useEffect, useRef } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Wallet, Menu, X, UserPlus, LogIn } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { NAV_ITEMS, isNavItemActive } from '@/lib/nav';

const FOCUSABLE = 'a, button, [tabindex]:not([tabindex="-1"])';

const Navigation = () => {
  const [isOpen, setIsOpen] = useState(false);
  const location = useLocation();
  const toggleRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  // Close mobile menu on Escape
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && isOpen) {
        setIsOpen(false);
      }
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [isOpen]);

  // Focus first item when menu opens; return focus to toggle when it closes
  useEffect(() => {
    if (isOpen) {
      const first = menuRef.current?.querySelector<HTMLElement>(FOCUSABLE);
      first?.focus();
    } else {
      toggleRef.current?.focus();
    }
  }, [isOpen]);

  const handleMenuKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (e.key !== 'Tab' || !menuRef.current) return;
    const focusable = Array.from(menuRef.current.querySelectorAll<HTMLElement>(FOCUSABLE));
    if (focusable.length === 0) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault();
      last.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault();
      first.focus();
    }
  };

  return (
    <nav aria-label="Верхняя навигация" className="bg-[#0B1929]/80 backdrop-blur-md border-b border-white/10">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between h-16">
          <div className="flex items-center">
            <Link to="/" className="flex items-center space-x-2">
              <div className="w-8 h-8 bg-emerald-500/20 rounded-lg flex items-center justify-center">
                <Wallet aria-hidden="true" className="w-5 h-5 text-emerald-400" />
              </div>
              <span className="text-xl font-bold text-emerald-400">
                Мои финансы
              </span>
            </Link>
          </div>

          {/* Desktop Navigation */}
          <div className="hidden md:flex items-center space-x-8">
            {NAV_ITEMS.map(item => {
              const { href, label, icon: Icon, linkTo } = item;
              const active = isNavItemActive(item, location.pathname);
              return (
                <Link
                  key={href}
                  to={linkTo}
                  aria-current={active ? 'page' : undefined}
                  className={cn(
                    "flex items-center space-x-2 px-3 py-2 rounded-xl text-sm font-medium transition-all duration-200",
                    active
                      ? "text-emerald-400 bg-emerald-500/10"
                      : "text-dashboard-text-muted hover:text-dashboard-text hover:bg-white/5"
                  )}
                >
                  <Icon aria-hidden="true" className="w-4 h-4" />
                  <span>{label}</span>
                </Link>
              );
            })}
            <div className="flex items-center space-x-2">
              <Link
                to="/login"
                className="inline-flex items-center text-sm font-medium px-3 py-1.5 bg-white/5 text-dashboard-text-muted border border-white/10 hover:bg-white/10 hover:text-dashboard-text rounded-xl transition-colors"
              >
                <LogIn aria-hidden="true" className="w-4 h-4 mr-2" />
                Войти
              </Link>
              <Link
                to="/register"
                className="inline-flex items-center text-sm font-medium px-3 py-1.5 bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 hover:bg-emerald-500/30 rounded-xl transition-colors"
              >
                <UserPlus aria-hidden="true" className="w-4 h-4 mr-2" />
                Регистрация
              </Link>
            </div>
          </div>

          {/* Mobile menu button */}
          <div className="md:hidden flex items-center">
            <Button
              ref={toggleRef}
              variant="ghost"
              size="sm"
              aria-expanded={isOpen}
              aria-controls="mobile-menu"
              aria-label={isOpen ? 'Закрыть меню' : 'Открыть меню'}
              className="text-dashboard-text-muted hover:text-dashboard-text hover:bg-white/5"
              onClick={() => setIsOpen(!isOpen)}
            >
              {isOpen ? <X aria-hidden="true" className="w-5 h-5" /> : <Menu aria-hidden="true" className="w-5 h-5" />}
            </Button>
          </div>
        </div>

        {/* Mobile Navigation */}
        {isOpen && (
          <div ref={menuRef} id="mobile-menu" aria-label="Мобильное меню" className="md:hidden bg-[#0B1929]/95 backdrop-blur-md" onKeyDown={handleMenuKeyDown}>
            <div className="px-2 pt-2 pb-3 space-y-1 sm:px-3">
              {NAV_ITEMS.map(item => {
                const { href, label, icon: Icon, linkTo } = item;
                const active = isNavItemActive(item, location.pathname);
                return (
                  <Link
                    key={href}
                    to={linkTo}
                    aria-current={active ? 'page' : undefined}
                    onClick={() => setIsOpen(false)}
                    className={cn(
                      "flex items-center space-x-2 px-3 py-2 rounded-xl text-sm font-medium transition-all duration-200",
                      active
                        ? "text-emerald-400 bg-emerald-500/10"
                        : "text-dashboard-text-muted hover:text-dashboard-text hover:bg-white/5"
                    )}
                  >
                    <Icon aria-hidden="true" className="w-4 h-4" />
                    <span>{label}</span>
                  </Link>
                );
              })}
              <div className="px-3 py-2 border-t border-white/10 mt-2 pt-2">
                <div className="space-y-2">
                  <Link
                    to="/login"
                    onClick={() => setIsOpen(false)}
                    className="w-full flex items-center text-sm font-medium px-3 py-1.5 bg-white/5 text-dashboard-text-muted border border-white/10 hover:bg-white/10 hover:text-dashboard-text rounded-xl transition-colors"
                  >
                    <LogIn aria-hidden="true" className="w-4 h-4 mr-2" />
                    Войти
                  </Link>
                  <Link
                    to="/register"
                    onClick={() => setIsOpen(false)}
                    className="w-full flex items-center text-sm font-medium px-3 py-1.5 bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 hover:bg-emerald-500/30 rounded-xl transition-colors"
                  >
                    <UserPlus aria-hidden="true" className="w-4 h-4 mr-2" />
                    Регистрация
                  </Link>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </nav>
  );
};

export default Navigation;

import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Home, Wallet, TrendingUp, Menu, X, UserPlus, LogIn } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

const Navigation = () => {
  const [isOpen, setIsOpen] = useState(false);
  const location = useLocation();

  const navItems = [
    { href: '/', label: 'Главная', icon: Home },
    { href: '/budget', label: 'Бюджет', icon: Wallet },
    { href: '/investments', label: 'Инвестиции', icon: TrendingUp },
  ];

  const isActive = (href: string) => location.pathname === href;

  return (
    <nav className="bg-[#0B1929]/80 backdrop-blur-md border-b border-white/10">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between h-16">
          <div className="flex items-center">
            <Link to="/" className="flex items-center space-x-2">
              <div className="w-8 h-8 bg-emerald-500/20 rounded-lg flex items-center justify-center">
                <Wallet className="w-5 h-5 text-emerald-400" />
              </div>
              <span className="text-xl font-bold text-emerald-400">
                Мои финансы
              </span>
            </Link>
          </div>

          {/* Desktop Navigation */}
          <div className="hidden md:flex items-center space-x-8">
            {navItems.map(({ href, label, icon: Icon }) => (
              <Link
                key={href}
                to={href}
                className={cn(
                  "flex items-center space-x-2 px-3 py-2 rounded-xl text-sm font-medium transition-all duration-200",
                  isActive(href)
                    ? "text-emerald-400 bg-emerald-500/10"
                    : "text-dashboard-text-muted hover:text-dashboard-text hover:bg-white/5"
                )}
              >
                <Icon className="w-4 h-4" />
                <span>{label}</span>
              </Link>
            ))}
            <div className="flex items-center space-x-2">
              <Link to="/login">
                <button className="inline-flex items-center text-sm font-medium px-3 py-1.5 bg-white/5 text-dashboard-text-muted border border-white/10 hover:bg-white/10 hover:text-dashboard-text rounded-xl transition-colors">
                  <LogIn className="w-4 h-4 mr-2" />
                  Войти
                </button>
              </Link>
              <Link to="/register">
                <button className="inline-flex items-center text-sm font-medium px-3 py-1.5 bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 hover:bg-emerald-500/30 rounded-xl transition-colors">
                  <UserPlus className="w-4 h-4 mr-2" />
                  Регистрация
                </button>
              </Link>
            </div>
          </div>

          {/* Mobile menu button */}
          <div className="md:hidden flex items-center">
            <Button
              variant="ghost"
              size="sm"
              className="text-dashboard-text-muted hover:text-dashboard-text hover:bg-white/5"
              onClick={() => setIsOpen(!isOpen)}
            >
              {isOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
            </Button>
          </div>
        </div>

        {/* Mobile Navigation */}
        {isOpen && (
          <div className="md:hidden bg-[#0B1929]/95 backdrop-blur-md">
            <div className="px-2 pt-2 pb-3 space-y-1 sm:px-3">
              {navItems.map(({ href, label, icon: Icon }) => (
                <Link
                  key={href}
                  to={href}
                  onClick={() => setIsOpen(false)}
                  className={cn(
                    "flex items-center space-x-2 px-3 py-2 rounded-xl text-sm font-medium transition-all duration-200",
                    isActive(href)
                      ? "text-emerald-400 bg-emerald-500/10"
                      : "text-dashboard-text-muted hover:text-dashboard-text hover:bg-white/5"
                  )}
                >
                  <Icon className="w-4 h-4" />
                  <span>{label}</span>
                </Link>
              ))}
              <div className="px-3 py-2 border-t border-white/10 mt-2 pt-2">
                <div className="space-y-2">
                  <Link to="/login" onClick={() => setIsOpen(false)}>
                    <button className="w-full flex items-center text-sm font-medium px-3 py-1.5 bg-white/5 text-dashboard-text-muted border border-white/10 hover:bg-white/10 hover:text-dashboard-text rounded-xl transition-colors">
                      <LogIn className="w-4 h-4 mr-2" />
                      Войти
                    </button>
                  </Link>
                  <Link to="/register" onClick={() => setIsOpen(false)}>
                    <button className="w-full flex items-center text-sm font-medium px-3 py-1.5 bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 hover:bg-emerald-500/30 rounded-xl transition-colors">
                      <UserPlus className="w-4 h-4 mr-2" />
                      Регистрация
                    </button>
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

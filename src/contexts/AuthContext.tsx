import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { apiLogout } from '@/lib/api';

interface User {
  userId: string;
  email: string;
  firstName?: string;
  lastName?: string;
  name?: string;
  isActive: boolean;
  isVerified: boolean;
  roles: string[];
  lastLoginAt: string;
}

interface AuthContextType {
  isAuthenticated: boolean;
  user: User | null;
  accessToken: string | null;
  login: (email: string, name?: string) => void;
  logout: () => Promise<void>;
  setAuthData: (accessToken: string, refreshToken: string, user: User) => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

interface AuthProviderProps {
  children: ReactNode;
}

export const AuthProvider = ({ children }: AuthProviderProps) => {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [user, setUser] = useState<User | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);

  // Проверяем наличие токенов при инициализации
  useEffect(() => {
    const token = localStorage.getItem('accessToken');
    const userData = localStorage.getItem('user');

    if (token && userData && userData !== 'undefined' && userData !== 'null') {
      try {
        setAccessToken(token);
        setUser(JSON.parse(userData));
        setIsAuthenticated(true);
      } catch (error) {
        console.error('Failed to parse user data from localStorage:', error);
        // Очищаем некорректные данные
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        localStorage.removeItem('user');
      }
    }
  }, []);

  const setAuthData = (newAccessToken: string, refreshToken: string, userData: User) => {
    localStorage.setItem('accessToken', newAccessToken);
    localStorage.setItem('refreshToken', refreshToken);
    localStorage.setItem('user', JSON.stringify(userData));

    setAccessToken(newAccessToken);
    setUser(userData);
    setIsAuthenticated(true);
  };

  const login = (email: string, name = 'Пользователь') => {
    setIsAuthenticated(true);
    // Временная поддержка старого формата для обратной совместимости
    const tempUser: User = {
      userId: '',
      email,
      isActive: true,
      isVerified: false,
      roles: ['USER'],
      lastLoginAt: new Date().toISOString(),
    };
    setUser(tempUser);
  };

  const logout = async () => {
    // Вызываем API logout - если не удалось, выбрасываем ошибку
    await apiLogout(false);

    // Очищаем локальные данные только после успешного logout на бэкенде
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('user');

    setIsAuthenticated(false);
    setUser(null);
    setAccessToken(null);

    // Перенаправляем на страницу логина
    window.location.href = '/login';
  };

  return (
    <AuthContext.Provider value={{ isAuthenticated, user, accessToken, login, logout, setAuthData }}>
      {children}
    </AuthContext.Provider>
  );
};
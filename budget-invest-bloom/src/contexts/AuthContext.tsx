// TODO security: переехать с localStorage на HttpOnly cookies для accessToken
// (требует поддержки на стороне auth-сервиса). Сейчас токен уязвим к XSS.
import {createContext, useContext, useState, useEffect, ReactNode, useMemo, useCallback} from 'react';
import {useNavigate} from 'react-router-dom';
import {apiLogout} from '@/lib/api';

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
    isInitialized: boolean;
    user: User | null;
    accessToken: string | null;
    logout: () => Promise<void>;
    setAuthData: (accessToken: string, user: User) => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

// eslint-disable-next-line react-refresh/only-export-components
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

export const AuthProvider = ({children}: AuthProviderProps) => {
    const [isAuthenticated, setIsAuthenticated] = useState(false);
    const [isInitialized, setIsInitialized] = useState(false);
    const [user, setUser] = useState<User | null>(null);
    const [accessToken, setAccessToken] = useState<string | null>(null);
    const navigate = useNavigate();

    // Check for tokens on initialization
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
                // Clear invalid data
                localStorage.removeItem('accessToken');
                localStorage.removeItem('user');
            }
        }

        setIsInitialized(true);
    }, []);

    // Listen for session expiry event dispatched by api.ts
    useEffect(() => {
        const handleAuthExpired = () => {
            localStorage.removeItem('accessToken');
            localStorage.removeItem('user');
            setIsAuthenticated(false);
            setUser(null);
            setAccessToken(null);
            navigate('/login');
        };

        window.addEventListener('auth:expired', handleAuthExpired);
        return () => window.removeEventListener('auth:expired', handleAuthExpired);
    }, [navigate]);

    const setAuthData = useCallback((newAccessToken: string, userData: User) => {
        localStorage.setItem('accessToken', newAccessToken);
        localStorage.setItem('user', JSON.stringify(userData));

        setAccessToken(newAccessToken);
        setUser(userData);
        setIsAuthenticated(true);
    }, []);

    const logout = useCallback(async () => {
        try {
            // Try to log out from the current session (requires cookie)
            await apiLogout(false);
        } catch (error) {
            // If logout failed (e.g. network error) — just clear local state, do not
            // fall back to logoutFromAll as that would sign the user out of every device.
            console.warn('Logout API call failed, clearing local state only', error);
        }

        // Clear local data
        // refreshToken is removed by the backend via Set-Cookie
        localStorage.removeItem('accessToken');
        localStorage.removeItem('user');
        localStorage.removeItem('refreshToken'); // In case an old token is stored

        setIsAuthenticated(false);
        setUser(null);
        setAccessToken(null);

        navigate('/login');
    }, [navigate]);

    const value = useMemo(
        () => ({isAuthenticated, isInitialized, user, accessToken, logout, setAuthData}),
        [isAuthenticated, isInitialized, user, accessToken, logout, setAuthData]
    );

    return (
        <AuthContext.Provider value={value}>
            {children}
        </AuthContext.Provider>
    );
};

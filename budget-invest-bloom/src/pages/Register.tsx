import * as React from 'react';
import {useState} from 'react';
import {Link, useNavigate} from 'react-router-dom';
import {Button} from '@/components/ui/button';
import {Input} from '@/components/ui/input';
import {Label} from '@/components/ui/label';
import {CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card';
import {useToast} from '@/hooks/use-toast';
import {Eye, EyeOff, Lock, Mail, User} from 'lucide-react';

const Register = () => {
    const [showPassword, setShowPassword] = useState(false);
    const [showConfirmPassword, setShowConfirmPassword] = useState(false);
    const [formData, setFormData] = useState({
        firstName: '',
        lastName: '',
        email: '',
        password: '',
        confirmPassword: ''
    });
    const [isLoading, setIsLoading] = useState(false);
    const {toast} = useToast();
    const navigate = useNavigate();

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setIsLoading(true);

        // Валидация совпадения паролей
        if (formData.password !== formData.confirmPassword) {
            toast({
                title: "Ошибка",
                description: "Пароли не совпадают",
                variant: "destructive",
            });
            setIsLoading(false);
            return;
        }

        try {
            const response = await fetch(`${import.meta.env.VITE_API_BASE_URL}/auth/api/register`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                credentials: 'include',
                body: JSON.stringify({
                    data: {
                        firstName: formData.firstName,
                        lastName: formData.lastName,
                        email: formData.email,
                        password: formData.password,
                    }
                }),
            });

            if (!response.ok) {
                const errorData = await response.json();
                let errorMessage = errorData.message || 'Ошибка регистрации';

                if (errorData.body && typeof errorData.body === 'object') {
                    const validationErrors = Object.values(errorData.body).filter(Boolean).map(String);
                    if (validationErrors.length > 0) {
                        errorMessage = validationErrors.join(', ');
                    }
                }

                toast({
                    title: "Ошибка",
                    description: errorMessage,
                    variant: "destructive",
                });
                return;
            }

            toast({
                title: "Успешно!",
                description: "Аккаунт создан. Проверьте email для подтверждения.",
            });

            // Перенаправление на страницу логина
            setTimeout(() => {
                navigate('/login');
            }, 2000);
        } catch (error) {
            console.error('Registration error:', error);
            toast({
                title: "Ошибка",
                description: "Не удалось создать аккаунт. Попробуйте снова.",
                variant: "destructive",
            });
        } finally {
            setIsLoading(false);
        }
    };

    const handleInputChange = (field: string, value: string) => {
        setFormData(prev => ({...prev, [field]: value}));
    };

    return (
        <div className="dashboard-bg min-h-screen flex items-center justify-center p-4">
            <div className="glass-card w-full max-w-md p-0 animate-fade-slide-up" style={{ borderLeft: '3px solid #10B981' }}>
                <CardHeader className="text-center pt-8">
                    <div
                        className="w-12 h-12 bg-emerald-500/10 rounded-xl flex items-center justify-center mx-auto mb-4 border border-emerald-500/20"
                        style={{ boxShadow: '0 0 20px rgba(16,185,129,0.25)' }}>
                        <User className="w-6 h-6 text-emerald-400"/>
                    </div>
                    <CardTitle className="text-2xl font-bold text-emerald-400">Создать аккаунт</CardTitle>
                    <CardDescription className="text-dashboard-text-muted">
                        Заполните данные для создания нового аккаунта
                    </CardDescription>
                </CardHeader>
                <CardContent className="pb-8">
                    <form onSubmit={handleSubmit} className="space-y-4">
                        <div className="grid grid-cols-2 gap-4">
                            <div className="space-y-2">
                                <Label htmlFor="firstName" className="text-dashboard-text-muted">Имя</Label>
                                <Input
                                    id="firstName"
                                    type="text"
                                    placeholder="Иван"
                                    className="text-dashboard-text placeholder:text-dashboard-text-muted focus:border-emerald-500/50 transition-colors"
                                    value={formData.firstName}
                                    onChange={(e) => handleInputChange('firstName', e.target.value)}
                                    required
                                />
                            </div>
                            <div className="space-y-2">
                                <Label htmlFor="lastName" className="text-dashboard-text-muted">Фамилия</Label>
                                <Input
                                    id="lastName"
                                    type="text"
                                    placeholder="Иванов"
                                    className="text-dashboard-text placeholder:text-dashboard-text-muted focus:border-emerald-500/50 transition-colors"
                                    value={formData.lastName}
                                    onChange={(e) => handleInputChange('lastName', e.target.value)}
                                    required
                                />
                            </div>
                        </div>

                        <div className="space-y-2">
                            <Label htmlFor="email" className="text-dashboard-text-muted">Email</Label>
                            <div className="relative">
                                <Mail className="absolute left-3 top-3 h-4 w-4 text-dashboard-text-muted"/>
                                <Input
                                    id="email"
                                    type="email"
                                    placeholder="your@email.com"
                                    className="pl-10 text-dashboard-text placeholder:text-dashboard-text-muted focus:border-emerald-500/50 transition-colors"
                                    value={formData.email}
                                    onChange={(e) => handleInputChange('email', e.target.value)}
                                    required
                                />
                            </div>
                        </div>

                        <div className="space-y-2">
                            <Label htmlFor="password" className="text-dashboard-text-muted">Пароль</Label>
                            <div className="relative">
                                <Lock className="absolute left-3 top-3 h-4 w-4 text-dashboard-text-muted"/>
                                <Input
                                    id="password"
                                    type={showPassword ? "text" : "password"}
                                    placeholder="Введите пароль"
                                    className="pl-10 pr-10 text-dashboard-text placeholder:text-dashboard-text-muted focus:border-emerald-500/50 transition-colors"
                                    value={formData.password}
                                    onChange={(e) => handleInputChange('password', e.target.value)}
                                    required
                                />
                                <Button
                                    type="button"
                                    variant="ghost"
                                    size="sm"
                                    className="absolute right-0 top-0 h-full px-3 py-2 hover:bg-transparent text-dashboard-text-muted hover:text-dashboard-text"
                                    onClick={() => setShowPassword(!showPassword)}
                                >
                                    {showPassword ? (
                                        <EyeOff className="h-4 w-4"/>
                                    ) : (
                                        <Eye className="h-4 w-4"/>
                                    )}
                                </Button>
                            </div>
                        </div>

                        <div className="space-y-2">
                            <Label htmlFor="confirmPassword" className="text-dashboard-text-muted">Подтвердите пароль</Label>
                            <div className="relative">
                                <Lock className="absolute left-3 top-3 h-4 w-4 text-dashboard-text-muted"/>
                                <Input
                                    id="confirmPassword"
                                    type={showConfirmPassword ? "text" : "password"}
                                    placeholder="Повторите пароль"
                                    className="pl-10 pr-10 text-dashboard-text placeholder:text-dashboard-text-muted focus:border-emerald-500/50 transition-colors"
                                    value={formData.confirmPassword}
                                    onChange={(e) => handleInputChange('confirmPassword', e.target.value)}
                                    required
                                />
                                <Button
                                    type="button"
                                    variant="ghost"
                                    size="sm"
                                    className="absolute right-0 top-0 h-full px-3 py-2 hover:bg-transparent text-dashboard-text-muted hover:text-dashboard-text"
                                    onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                                >
                                    {showConfirmPassword ? (
                                        <EyeOff className="h-4 w-4"/>
                                    ) : (
                                        <Eye className="h-4 w-4"/>
                                    )}
                                </Button>
                            </div>
                        </div>

                        <Button
                            type="submit"
                            className="w-full bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 hover:bg-emerald-500/30 rounded-xl transition-colors h-10"
                            disabled={isLoading}
                        >
                            {isLoading ? "Создание аккаунта..." : "Создать аккаунт"}
                        </Button>
                    </form>

                    <div className="mt-6 pt-6 border-t border-white/10 text-center">
                        <p className="text-sm text-dashboard-text-muted">
                            Уже есть аккаунт?{' '}
                            <Link to="/login" className="text-emerald-400 font-medium hover:text-emerald-300 transition-colors hover:underline">
                                Войти
                            </Link>
                        </p>
                    </div>
                </CardContent>
            </div>
        </div>
    );
};

export default Register;

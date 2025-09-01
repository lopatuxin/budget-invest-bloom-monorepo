import { ReactNode } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { cn } from '@/lib/utils';

interface FinanceCardProps {
  title: string;
  value: string;
  icon: ReactNode;
  trend?: {
    value: string;
    isPositive: boolean;
  };
  className?: string;
  gradient?: 'primary' | 'secondary' | 'success';
  onClick?: () => void;
}

const FinanceCard = ({ 
  title, 
  value, 
  icon, 
  trend, 
  className,
  gradient = 'primary',
  onClick
}: FinanceCardProps) => {
  const gradientClasses = {
    primary: 'bg-gradient-primary',
    secondary: 'bg-gradient-secondary', 
    success: 'bg-gradient-success'
  };

  return (
    <Card 
      className={cn(
        "relative overflow-hidden transition-all duration-300 hover:shadow-card hover:scale-105 border-0",
        onClick && "cursor-pointer",
        className
      )}
      onClick={onClick}
    >
      <div className={cn(
        "absolute inset-0 opacity-5",
        gradientClasses[gradient]
      )} />
      
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2 relative z-10">
        <CardTitle className="text-sm font-medium text-muted-foreground">
          {title}
        </CardTitle>
        <div className={cn(
          "p-2 rounded-lg",
          gradientClasses[gradient]
        )}>
          <div className="text-primary-foreground">
            {icon}
          </div>
        </div>
      </CardHeader>
      
      <CardContent className="relative z-10">
        <div className="text-2xl font-bold text-foreground mb-1">
          {value}
        </div>
        {trend && (
          <p className={cn(
            "text-xs flex items-center",
            trend.isPositive ? "text-primary" : "text-destructive"
          )}>
            <span className={cn(
              "mr-1",
              trend.isPositive ? "text-primary" : "text-destructive"
            )}>
              {trend.isPositive ? "↗" : "↘"}
            </span>
            {trend.value} за месяц
          </p>
        )}
      </CardContent>
    </Card>
  );
};

export default FinanceCard;
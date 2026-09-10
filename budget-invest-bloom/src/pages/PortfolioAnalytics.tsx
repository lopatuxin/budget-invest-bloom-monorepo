import { Link } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/button';
import CompoundInterestCalculator from '@/components/investments/CompoundInterestCalculator';

const PortfolioAnalytics = () => {
  return (
    <div className="flex flex-col gap-4 lg:gap-5 pb-6">
      <div className="flex items-center justify-between h-11">
        <h1 className="font-display text-[26px] lg:text-[34px] leading-none text-app-text">Прогноз</h1>
        <Button asChild variant="outline" size="sm" className="gap-1.5 border-app-border-strong bg-app-surface text-app-text hover:bg-app-surface-2">
          <Link to="/investments">
            <ArrowLeft aria-hidden="true" className="w-3.5 h-3.5" />
            Инвестиции
          </Link>
        </Button>
      </div>

      <CompoundInterestCalculator />
    </div>
  );
};

export default PortfolioAnalytics;

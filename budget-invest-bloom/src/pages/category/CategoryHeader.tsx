import { Link } from 'react-router-dom';
import { ArrowLeft, MoreHorizontal, Pencil, Trash2 } from 'lucide-react';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { MonthSwitcher } from '@/components/MonthSwitcher';

interface CategoryHeaderProps {
  categoryName: string;
  emoji?: string;
  isSystem: boolean;
  backHref: string;
  month: number;
  year: number;
  monthSubtitle: string;
  isMonthLoading: boolean;
  onPrevMonth: () => void;
  onNextMonth: () => void;
  onEditRequest: () => void;
  onDeleteRequest: () => void;
}

const ICON_BUTTON_CLASS =
  'flex shrink-0 items-center justify-center w-10 h-10 lg:w-[34px] lg:h-[34px] rounded-lg border border-app-border-strong text-app-text hover:bg-app-surface-2 transition-colors';

export function CategoryHeader({
  categoryName,
  emoji,
  isSystem,
  backHref,
  month,
  year,
  monthSubtitle,
  isMonthLoading,
  onPrevMonth,
  onNextMonth,
  onEditRequest,
  onDeleteRequest,
}: CategoryHeaderProps) {
  return (
    <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
      <div className="flex items-center gap-3 h-11 lg:h-[44px]">
        <Link to={backHref} aria-label="К бюджету" className={ICON_BUTTON_CLASS}>
          <ArrowLeft aria-hidden="true" className="w-4 h-4" />
        </Link>
        <div className="w-8 h-8 lg:w-10 lg:h-10 rounded-lg lg:rounded-[10px] bg-app-surface-2 border border-app-border flex items-center justify-center text-base lg:text-[22px] shrink-0">
          {emoji || categoryName.charAt(0).toUpperCase()}
        </div>
        <h1 className="flex-1 min-w-0 lg:flex-initial font-display text-2xl lg:text-[34px] leading-none text-app-text truncate">
          {categoryName}
        </h1>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button type="button" aria-label="Действия с категорией" className={ICON_BUTTON_CLASS}>
              <MoreHorizontal aria-hidden="true" className="w-4 h-4" />
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="bg-app-surface border-app-border text-app-text">
            <DropdownMenuItem disabled={isSystem} onSelect={onEditRequest} className="cursor-pointer">
              <Pencil aria-hidden="true" className="w-4 h-4 mr-2" />
              {isSystem ? 'Переименовать (системная категория)' : 'Переименовать или сменить эмодзи'}
            </DropdownMenuItem>
            <DropdownMenuItem
              disabled={isSystem}
              onSelect={onDeleteRequest}
              className="cursor-pointer text-app-bad focus:text-app-bad"
            >
              <Trash2 aria-hidden="true" className="w-4 h-4 mr-2" />
              {isSystem ? 'Удалить (системная категория)' : 'Удалить категорию'}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
      <div className="mx-auto lg:mx-0">
        <MonthSwitcher
          month={month}
          year={year}
          subtitle={monthSubtitle}
          isLoading={isMonthLoading}
          onPrevMonth={onPrevMonth}
          onNextMonth={onNextMonth}
        />
      </div>
    </div>
  );
}

import { useState } from 'react';
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { isCategoryHasExpensesError, useDeleteCategory } from '@/hooks/useDeleteCategory';
import { pluralize } from '@/lib/dateOptions';
import { EXPENSES_WORD } from '@/pages/category/categoryFormat';
import type { CategoryHasExpensesErrorBody } from '@/types/budget';

interface CategoryDeleteFlowProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  categoryId: string;
  categoryName: string;
  onDeleted: () => void;
}

// Two-step confirmation: the first attempt runs with force=false, and a 409
// CATEGORY_HAS_EXPENSES response opens the second step with the real expense count.
export function CategoryDeleteFlow({ open, onOpenChange, categoryId, categoryName, onDeleted }: CategoryDeleteFlowProps) {
  const deleteCategory = useDeleteCategory();
  const [cascade, setCascade] = useState<{ open: boolean; expenseCount: number }>({ open: false, expenseCount: 0 });

  const runDelete = (force: boolean) => {
    deleteCategory.mutate(
      { categoryId, force },
      {
        onSuccess: () => {
          setCascade({ open: false, expenseCount: 0 });
          onOpenChange(false);
          onDeleted();
        },
        onError: (error: unknown) => {
          if (!force && isCategoryHasExpensesError(error)) {
            // isCategoryHasExpensesError only checks status/code; the count itself still needs
            // the details cast, since ApiError.details is typed unknown.
            const { expenseCount } = error.details as CategoryHasExpensesErrorBody;
            onOpenChange(false);
            setCascade({ open: true, expenseCount });
          }
        },
      }
    );
  };

  return (
    <>
      <AlertDialog open={open} onOpenChange={onOpenChange}>
        <AlertDialogContent className="bg-app-surface border-app-border text-app-text">
          <AlertDialogHeader>
            <AlertDialogTitle>Удалить категорию «{categoryName}»?</AlertDialogTitle>
            <AlertDialogDescription className="text-app-text-muted">
              Если в категории есть расходы, спросим ещё раз
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel className="border-app-border-strong bg-app-surface text-app-text hover:bg-app-surface-2">
              Отмена
            </AlertDialogCancel>
            <Button onClick={() => runDelete(false)} disabled={deleteCategory.isPending} className="bg-app-bad text-white hover:bg-app-bad/90">
              {deleteCategory.isPending ? 'Удаление...' : 'Удалить'}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={cascade.open} onOpenChange={(next) => setCascade((state) => ({ ...state, open: next }))}>
        <AlertDialogContent className="bg-app-surface border-app-border text-app-text">
          <AlertDialogHeader>
            <AlertDialogTitle>Удалить вместе с расходами?</AlertDialogTitle>
            <AlertDialogDescription className="text-app-text-muted">
              В категории {pluralize(cascade.expenseCount, EXPENSES_WORD)}, все они будут удалены безвозвратно
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel className="border-app-border-strong bg-app-surface text-app-text hover:bg-app-surface-2">
              Отмена
            </AlertDialogCancel>
            <Button onClick={() => runDelete(true)} disabled={deleteCategory.isPending} className="bg-app-bad text-white hover:bg-app-bad/90">
              {deleteCategory.isPending ? 'Удаление...' : 'Удалить всё'}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}

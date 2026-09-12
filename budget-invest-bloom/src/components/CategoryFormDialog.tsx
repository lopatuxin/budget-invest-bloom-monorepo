import { useState, type FormEvent } from 'react';
import { Loader2 } from 'lucide-react';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Drawer, DrawerContent, DrawerHeader, DrawerTitle } from '@/components/ui/drawer';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { CategoryEmojiPicker } from '@/components/CategoryEmojiPicker';
import { MOBILE_BREAKPOINT, useIsNarrowerThan } from '@/hooks/use-mobile';
import { useCreateCategory } from '@/hooks/useCreateCategory';
import { useUpdateCategory } from '@/hooks/useUpdateCategory';
import { isCategoryNameTakenError } from '@/lib/api';

interface CategoryFormDialogInitial {
  id: string;
  name: string;
  emoji?: string;
}

// Editing needs the category being edited; creation has nothing to seed the form with. A
// union instead of an optional `initial` makes mode="edit" without it a type error instead
// of an `initial!.id` that only fails at runtime.
type CategoryFormMode = { mode: 'create' } | { mode: 'edit'; initial: CategoryFormDialogInitial };

type CategoryFormDialogProps = CategoryFormMode & {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSaved?: (name: string) => void;
  // Width below which the dialog renders as a bottom drawer instead of a centered window.
  // Defaults to the app-wide MOBILE_BREAKPOINT (Budget.tsx's create dialog); the category page
  // passes its own 1024, matching the width where its own layout switches to the phone one —
  // MOBILE_BREAKPOINT itself is shared by dialogs across the whole app and must not move.
  mobileBreakpoint?: number;
};

// Category limits (the "budget" field) are out of scope for this dialog in both
// modes — creation always sends 0, editing never sends the field at all.
export function CategoryFormDialog(props: CategoryFormDialogProps) {
  const { open, onOpenChange, onSaved, mobileBreakpoint = MOBILE_BREAKPOINT } = props;
  const isMobile = useIsNarrowerThan(mobileBreakpoint);
  const title = props.mode === 'create' ? 'Новая категория' : 'Переименовать или сменить эмодзи';
  const handleClose = () => onOpenChange(false);

  // Mounted only while open, like OperationDialogProvider mounts OperationForm — so every
  // opening starts from `initial` fresh instead of an effect re-seeding state a frame late.
  const form =
    open &&
    (props.mode === 'create' ? (
      <CategoryForm mode="create" onSaved={onSaved} onClose={handleClose} />
    ) : (
      <CategoryForm mode="edit" initial={props.initial} onSaved={onSaved} onClose={handleClose} />
    ));

  if (isMobile) {
    return (
      <Drawer open={open} onOpenChange={onOpenChange}>
        <DrawerContent className="bg-app-surface border-app-border text-app-text px-4 pb-6">
          <DrawerHeader>
            <DrawerTitle className="text-app-text">{title}</DrawerTitle>
          </DrawerHeader>
          {form}
        </DrawerContent>
      </Drawer>
    );
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="sm:max-w-[400px] border-app-border text-app-text"
        style={{ background: 'rgb(var(--app-surface))' }}
      >
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
        </DialogHeader>
        {form}
      </DialogContent>
    </Dialog>
  );
}

type CategoryFormProps = CategoryFormMode & {
  onSaved?: (name: string) => void;
  onClose: () => void;
};

function CategoryForm(props: CategoryFormProps) {
  const { onSaved, onClose } = props;
  const initial = props.mode === 'edit' ? props.initial : undefined;
  const [name, setName] = useState(initial?.name ?? '');
  const [emoji, setEmoji] = useState(initial?.emoji ?? '');
  const [nameError, setNameError] = useState<string | null>(null);
  const createCategory = useCreateCategory();
  const updateCategory = useUpdateCategory();
  const isPending = createCategory.isPending || updateCategory.isPending;

  // Both modes hit the same unique index, so both report a taken name the same way: under the
  // field, not as a toast. The hooks stay silent for this one error so it isn't reported twice.
  const showNameTakenInline = (error: unknown) => {
    // Cleared on any other error too, so a stale "name is taken" line can't sit under the field
    // while a toast reports something else about the retry.
    setNameError(isCategoryNameTakenError(error) ? error.message || 'Название уже занято' : null);
  };

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    const trimmed = name.trim();
    if (!trimmed || trimmed.length > 100) {
      setNameError('Введите название до 100 символов');
      return;
    }

    if (props.mode === 'create') {
      createCategory.mutate(
        { name: trimmed, budget: 0, emoji: emoji || undefined },
        { onSuccess: onClose, onError: showNameTakenInline }
      );
      return;
    }

    updateCategory.mutate(
      // Unlike creation, an edit must send emoji even when empty — the backend reads an
      // absent field as "don't change" and only an explicit "" as "clear it".
      { categoryId: props.initial.id, name: trimmed, emoji },
      {
        onSuccess: () => {
          onClose();
          onSaved?.(trimmed);
        },
        onError: showNameTakenInline,
      }
    );
  };

  const submitLabel = props.mode === 'create' ? 'Создать' : 'Сохранить';

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <div>
        <Label htmlFor="category-form-name" className="text-app-text-muted">
          Название
        </Label>
        <Input
          id="category-form-name"
          autoFocus
          maxLength={100}
          value={name}
          onChange={(e) => {
            setName(e.target.value);
            setNameError(null);
          }}
          placeholder="Например, «Продукты»"
          className="mt-1.5 bg-app-surface border-app-border-strong text-app-text placeholder:text-app-text-dim ring-offset-app-surface"
        />
        {nameError && <p className="mt-1 text-xs text-app-bad">{nameError}</p>}
      </div>
      <div>
        <Label className="text-app-text-muted">Эмодзи</Label>
        <div className="mt-1.5">
          <CategoryEmojiPicker value={emoji} onChange={setEmoji} />
        </div>
      </div>
      <div className="flex gap-2 pt-1">
        <Button
          type="button"
          variant="outline"
          className="flex-1 border-app-border-strong bg-app-surface text-app-text hover:bg-app-surface-2"
          onClick={onClose}
        >
          Отмена
        </Button>
        <Button
          type="submit"
          disabled={isPending}
          className="flex-1 bg-app-accent text-app-accent-ink hover:bg-app-accent/90"
        >
          {isPending && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
          {submitLabel}
        </Button>
      </div>
    </form>
  );
}

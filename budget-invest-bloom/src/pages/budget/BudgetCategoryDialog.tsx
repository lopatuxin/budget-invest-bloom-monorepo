import { useState, type FormEvent } from 'react';
import { Loader2 } from 'lucide-react';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { CategoryEmojiPicker } from '@/components/CategoryEmojiPicker';
import { useCreateCategory } from '@/hooks/useCreateCategory';

interface BudgetCategoryDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

// Category limits (the "budget" field) are out of scope for this dialog — the
// backend still stores the field, we just always send 0.
export function BudgetCategoryDialog({ open, onOpenChange }: BudgetCategoryDialogProps) {
  const [name, setName] = useState('');
  const [emoji, setEmoji] = useState('');
  const [nameError, setNameError] = useState<string | null>(null);
  const createCategory = useCreateCategory();

  const reset = () => {
    setName('');
    setEmoji('');
    setNameError(null);
  };

  const handleOpenChange = (next: boolean) => {
    if (!next) reset();
    onOpenChange(next);
  };

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    const trimmed = name.trim();
    if (!trimmed || trimmed.length > 100) {
      setNameError('Введите название до 100 символов');
      return;
    }
    createCategory.mutate(
      { name: trimmed, budget: 0, emoji: emoji || undefined },
      { onSuccess: () => handleOpenChange(false) }
    );
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent
        className="sm:max-w-[400px] border-app-border text-app-text"
        style={{ background: 'rgb(var(--app-surface))' }}
      >
        <DialogHeader>
          <DialogTitle>Новая категория</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div>
            <Label htmlFor="new-category-name" className="text-app-text-muted">
              Название
            </Label>
            <Input
              id="new-category-name"
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
              onClick={() => handleOpenChange(false)}
            >
              Отмена
            </Button>
            <Button
              type="submit"
              disabled={createCategory.isPending}
              className="flex-1 bg-app-accent text-app-accent-ink hover:bg-app-accent/90"
            >
              {createCategory.isPending && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
              Создать
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

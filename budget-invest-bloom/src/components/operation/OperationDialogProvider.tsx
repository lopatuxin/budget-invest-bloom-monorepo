import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog';
import { Drawer, DrawerContent, DrawerTitle } from '@/components/ui/drawer';
import { OperationForm } from '@/components/operation/OperationForm';
import { useIsMobile } from '@/hooks/use-mobile';
import type { OperationKind } from '@/types/budget';

interface OperationDialogContextValue {
  openOperationDialog: (kind: 'expense' | 'income') => void;
}

const OperationDialogContext = createContext<OperationDialogContextValue | undefined>(undefined);

// eslint-disable-next-line react-refresh/only-export-components
export function useOperationDialog(): OperationDialogContextValue {
  const context = useContext(OperationDialogContext);
  if (!context) {
    throw new Error('useOperationDialog must be used within an OperationDialogProvider');
  }
  return context;
}

interface OperationDialogProviderProps {
  children: ReactNode;
}

// Global "new operation" form, opened from any page (rail, bottom nav, empty states).
// Dialog on desktop, Drawer (vaul) on mobile — same OperationForm content either way.
export function OperationDialogProvider({ children }: OperationDialogProviderProps) {
  const [kind, setKind] = useState<OperationKind | null>(null);
  const isMobile = useIsMobile();

  const openOperationDialog = useCallback((k: 'expense' | 'income') => {
    setKind(k === 'expense' ? 'EXPENSE' : 'INCOME');
  }, []);

  const close = useCallback(() => setKind(null), []);

  const value = useMemo(() => ({ openOperationDialog }), [openOperationDialog]);

  return (
    <OperationDialogContext.Provider value={value}>
      {children}
      {isMobile ? (
        <Drawer open={kind !== null} onOpenChange={(open) => !open && close()}>
          <DrawerContent className="bg-app-surface border-app-border text-app-text px-4 pb-6">
            <DrawerTitle className="sr-only">Новая операция</DrawerTitle>
            {kind && <OperationForm initialKind={kind} onClose={close} />}
          </DrawerContent>
        </Drawer>
      ) : (
        <Dialog open={kind !== null} onOpenChange={(open) => !open && close()}>
          <DialogContent
            className="sm:max-w-[440px] border-app-border text-app-text"
            style={{ background: 'rgb(var(--app-surface))' }}
          >
            <DialogTitle className="sr-only">Новая операция</DialogTitle>
            {kind && <OperationForm initialKind={kind} onClose={close} />}
          </DialogContent>
        </Dialog>
      )}
    </OperationDialogContext.Provider>
  );
}

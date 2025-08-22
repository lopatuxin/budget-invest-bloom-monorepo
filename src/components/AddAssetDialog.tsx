import { useState } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from '@/components/ui/command';
import { Plus, Check, ChevronsUpDown } from 'lucide-react';
import { useToast } from '@/hooks/use-toast';
import { cn } from '@/lib/utils';

interface AddAssetDialogProps {
  onAddAsset: (asset: any) => void;
}

const AddAssetDialog = ({ onAddAsset }: AddAssetDialogProps) => {
  const [open, setOpen] = useState(false);
  const [comboboxOpen, setComboboxOpen] = useState(false);
  const [formData, setFormData] = useState({
    asset: '',
    shares: ''
  });
  const { toast } = useToast();

  // Моковые данные активов с Мосбиржи
  const availableAssets = [
    { symbol: 'SBER', name: 'ПАО Сбербанк', sector: 'Финансы', price: 285.50 },
    { symbol: 'GAZP', name: 'Газпром', sector: 'Прочее', price: 165.20 },
    { symbol: 'LKOH', name: 'ЛУКОЙЛ', sector: 'Прочее', price: 6850.00 },
    { symbol: 'YNDX', name: 'Яндекс', sector: 'Технологии', price: 2890.00 },
    { symbol: 'ROSN', name: 'Роснефть', sector: 'Прочее', price: 515.40 },
    { symbol: 'NVTK', name: 'НОВАТЭК', sector: 'Прочее', price: 1125.80 },
    { symbol: 'TCSG', name: 'TCS Group', sector: 'Финансы', price: 4250.00 },
    { symbol: 'MTSS', name: 'МТС', sector: 'Технологии', price: 295.60 },
    { symbol: 'MGNT', name: 'Магнит', sector: 'Потребительские товары', price: 4890.00 },
    { symbol: 'AFLT', name: 'Аэрофлот', sector: 'Прочее', price: 48.75 }
  ];

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    
    const selectedAsset = availableAssets.find(asset => asset.symbol === formData.asset);
    
    if (!formData.asset || !selectedAsset || !formData.shares) {
      toast({
        title: "Ошибка",
        description: "Пожалуйста, заполните все поля",
        variant: "destructive"
      });
      return;
    }

    const shares = parseFloat(formData.shares);
    
    if (isNaN(shares) || shares <= 0) {
      toast({
        title: "Ошибка", 
        description: "Количество акций должно быть положительным числом",
        variant: "destructive"
      });
      return;
    }

    const newAsset = {
      symbol: selectedAsset.symbol,
      name: selectedAsset.name,
      shares: shares,
      price: selectedAsset.price,
      value: shares * selectedAsset.price,
      change: 0,
      changePercent: 0,
      sector: selectedAsset.sector
    };

    onAddAsset(newAsset);
    setFormData({ asset: '', shares: '' });
    setOpen(false);
    
    toast({
      title: "Успешно",
      description: `Актив ${selectedAsset.symbol} добавлен в портфель`
    });
  };

  const selectedAsset = availableAssets.find(asset => asset.symbol === formData.asset);
  const totalValue = selectedAsset && formData.shares ? 
    parseFloat(formData.shares) * selectedAsset.price : 0;

  const handleInputChange = (field: string, value: string) => {
    setFormData(prev => ({ ...prev, [field]: value }));
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button 
          className="bg-gradient-primary hover:opacity-90"
          size="sm"
        >
          <Plus className="w-4 h-4 mr-2" />
          Добавить актив
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-[425px]">
        <DialogHeader>
          <DialogTitle>Добавить новый актив</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="asset">Выберите актив</Label>
            <Popover open={comboboxOpen} onOpenChange={setComboboxOpen}>
              <PopoverTrigger asChild>
                <Button
                  variant="outline"
                  role="combobox"
                  aria-expanded={comboboxOpen}
                  className="w-full justify-between"
                >
                  {formData.asset
                    ? availableAssets.find((asset) => asset.symbol === formData.asset)?.name
                    : "Выберите актив с Мосбиржи"}
                  <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
                </Button>
              </PopoverTrigger>
              <PopoverContent className="z-[60] w-[var(--radix-popover-trigger-width)] p-0">
                <Command>
                  <CommandInput autoFocus placeholder="Поиск активов..." />
                  <CommandList className="max-h-64 overflow-y-auto overscroll-contain">
                    <CommandEmpty>Активы не найдены.</CommandEmpty>
                    <CommandGroup>
                      {availableAssets.map((asset) => (
                        <CommandItem
                          key={asset.symbol}
                          value={`${asset.symbol} ${asset.name}`}
                          onSelect={(currentValue) => {
                            const selectedSymbol = asset.symbol;
                            handleInputChange('asset', selectedSymbol === formData.asset ? "" : selectedSymbol);
                            setComboboxOpen(false);
                          }}
                        >
                          <Check
                            className={cn(
                              "mr-2 h-4 w-4",
                              formData.asset === asset.symbol ? "opacity-100" : "opacity-0"
                            )}
                          />
                          <div className="flex flex-col">
                            <span className="font-medium">{asset.symbol}</span>
                            <span className="text-sm text-muted-foreground">{asset.name}</span>
                          </div>
                        </CommandItem>
                      ))}
                    </CommandGroup>
                  </CommandList>
                </Command>
              </PopoverContent>
            </Popover>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="shares">Количество акций</Label>
              <Input
                id="shares"
                type="number"
                placeholder="10"
                value={formData.shares}
                onChange={(e) => handleInputChange('shares', e.target.value)}
                min="0"
                step="1"
              />
            </div>
            
            <div className="space-y-2">
              <Label htmlFor="price">Цена за акцию (₽)</Label>
              <Input
                id="price"
                type="number"
                placeholder="Выберите актив"
                value={selectedAsset ? selectedAsset.price.toString() : ''}
                readOnly
                className="bg-muted/30"
              />
            </div>
          </div>

          <div className="bg-muted/20 p-4 rounded-lg">
            <div className="flex justify-between items-center">
              <span className="text-sm font-medium text-muted-foreground">Общая стоимость:</span>
              <span className="text-lg font-bold text-primary">
                ₽{totalValue.toLocaleString()}
              </span>
            </div>
          </div>


          <div className="flex justify-end space-x-2 pt-4">
            <Button 
              type="button" 
              variant="outline" 
              onClick={() => setOpen(false)}
            >
              Отмена
            </Button>
            <Button type="submit" className="bg-gradient-primary hover:opacity-90">
              Добавить актив
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
};

export default AddAssetDialog;
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
import { ScrollArea } from '@/components/ui/scroll-area';
interface AddAssetDialogProps {
  onAddAsset: (asset: any) => void;
}

const AddAssetDialog = ({ onAddAsset }: AddAssetDialogProps) => {
  const [open, setOpen] = useState(false);
  const [comboboxOpen, setComboboxOpen] = useState(false);
  const [query, setQuery] = useState('');
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

  const filteredAssets = availableAssets.filter(asset =>
    asset.symbol.toLowerCase().includes(query.toLowerCase()) ||
    asset.name.toLowerCase().includes(query.toLowerCase())
  );

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
      <DialogContent className="sm:max-w-[425px] bg-[#0B1929] border-white/10 text-dashboard-text">
        <DialogHeader>
          <DialogTitle>Добавить новый актив</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="asset" className="text-dashboard-text-muted">Выберите актив</Label>
            <Popover open={comboboxOpen} onOpenChange={setComboboxOpen}>
              <PopoverTrigger asChild>
                <Input
                  role="combobox"
                  aria-expanded={comboboxOpen}
                  placeholder="Выберите актив с Мосбиржи"
                  value={query}
                  onChange={(e) => {
                    setQuery(e.target.value);
                    setComboboxOpen(true);
                  }}
                  onFocus={() => setComboboxOpen(true)}
                  className="w-full bg-white/5 border-white/10 text-dashboard-text placeholder:text-dashboard-text-muted"
                />
              </PopoverTrigger>
              <PopoverContent className="z-[70] w-[var(--radix-popover-trigger-width)] p-0 bg-[#0B1929] border-white/10 shadow-lg">
                <ScrollArea className="h-64 w-full overscroll-contain" onWheelCapture={(e) => e.stopPropagation()} onTouchMoveCapture={(e) => e.stopPropagation()}>
                  {filteredAssets.length === 0 ? (
                    <div className="py-6 text-center text-sm text-dashboard-text-muted">
                      Активы не найдены
                    </div>
                  ) : (
                    <ul className="divide-y divide-border">
                      {filteredAssets.map((asset) => (
                        <li key={asset.symbol}>
                          <button
                            type="button"
                            className="w-full text-left px-3 py-2 hover:bg-white/5 focus:bg-white/5 focus:outline-none flex flex-col"
                            onClick={() => {
                              handleInputChange('asset', asset.symbol);
                              setQuery(asset.name);
                              setComboboxOpen(false);
                            }}
                          >
                            <span className="font-medium">{asset.symbol}</span>
                            <span className="text-sm text-dashboard-text-muted">{asset.name}</span>
                          </button>
                        </li>
                      ))}
                    </ul>
                  )}
                </ScrollArea>
              </PopoverContent>
            </Popover>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="shares" className="text-dashboard-text-muted">Количество акций</Label>
              <Input
                id="shares"
                type="number"
                placeholder="10"
                value={formData.shares}
                onChange={(e) => handleInputChange('shares', e.target.value)}
                min="0"
                step="1"
                className="bg-white/5 border-white/10 text-dashboard-text"
              />
            </div>
            
            <div className="space-y-2">
              <Label htmlFor="price" className="text-dashboard-text-muted">Цена за акцию (₽)</Label>
              <Input
                id="price"
                type="number"
                placeholder="Выберите актив"
                value={selectedAsset ? selectedAsset.price.toString() : ''}
                readOnly
                className="bg-white/5 border-white/10 text-dashboard-text"
              />
            </div>
          </div>

          <div className="bg-white/5 p-4 rounded-lg">
            <div className="flex justify-between items-center">
              <span className="text-sm font-medium text-dashboard-text-muted">Общая стоимость:</span>
              <span className="text-lg font-bold text-emerald-400">
                ₽{totalValue.toLocaleString()}
              </span>
            </div>
          </div>


          <div className="flex justify-end space-x-2 pt-4">
            <Button
              type="button"
              variant="outline"
              onClick={() => setOpen(false)}
              className="border-white/10 text-dashboard-text hover:bg-white/5"
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
import { useState } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Plus } from 'lucide-react';
import { useToast } from '@/hooks/use-toast';

interface AddAssetDialogProps {
  onAddAsset: (asset: any) => void;
}

const AddAssetDialog = ({ onAddAsset }: AddAssetDialogProps) => {
  const [open, setOpen] = useState(false);
  const [formData, setFormData] = useState({
    asset: '',
    shares: '',
    price: '',
    sector: ''
  });
  const { toast } = useToast();

  // Моковые данные активов с Мосбиржи
  const availableAssets = [
    { symbol: 'SBER', name: 'ПАО Сбербанк', sector: 'Финансы' },
    { symbol: 'GAZP', name: 'Газпром', sector: 'Прочее' },
    { symbol: 'LKOH', name: 'ЛУКОЙЛ', sector: 'Прочее' },
    { symbol: 'YNDX', name: 'Яндекс', sector: 'Технологии' },
    { symbol: 'ROSN', name: 'Роснефть', sector: 'Прочее' },
    { symbol: 'NVTK', name: 'НОВАТЭК', sector: 'Прочее' },
    { symbol: 'TCSG', name: 'TCS Group', sector: 'Финансы' },
    { symbol: 'MTSS', name: 'МТС', sector: 'Технологии' },
    { symbol: 'MGNT', name: 'Магнит', sector: 'Потребительские товары' },
    { symbol: 'AFLT', name: 'Аэрофлот', sector: 'Прочее' }
  ];

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    
    const selectedAsset = availableAssets.find(asset => asset.symbol === formData.asset);
    
    if (!formData.asset || !selectedAsset || !formData.shares || !formData.price) {
      toast({
        title: "Ошибка",
        description: "Пожалуйста, заполните все поля",
        variant: "destructive"
      });
      return;
    }

    const shares = parseFloat(formData.shares);
    const price = parseFloat(formData.price);
    
    if (isNaN(shares) || isNaN(price) || shares <= 0 || price <= 0) {
      toast({
        title: "Ошибка", 
        description: "Количество акций и цена должны быть положительными числами",
        variant: "destructive"
      });
      return;
    }

    const newAsset = {
      symbol: selectedAsset.symbol,
      name: selectedAsset.name,
      shares: shares,
      price: price,
      value: shares * price,
      change: 0,
      changePercent: 0,
      sector: selectedAsset.sector
    };

    onAddAsset(newAsset);
    setFormData({ asset: '', shares: '', price: '', sector: '' });
    setOpen(false);
    
    toast({
      title: "Успешно",
      description: `Актив ${selectedAsset.symbol} добавлен в портфель`
    });
  };

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
            <Select value={formData.asset} onValueChange={(value) => handleInputChange('asset', value)}>
              <SelectTrigger>
                <SelectValue placeholder="Выберите актив с Мосбиржи" />
              </SelectTrigger>
              <SelectContent>
                {availableAssets.map((asset) => (
                  <SelectItem key={asset.symbol} value={asset.symbol}>
                    <div className="flex flex-col">
                      <span className="font-medium">{asset.symbol}</span>
                      <span className="text-sm text-muted-foreground">{asset.name}</span>
                    </div>
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
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
                placeholder="175.5"
                value={formData.price}
                onChange={(e) => handleInputChange('price', e.target.value)}
                min="0"
                step="0.01"
              />
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
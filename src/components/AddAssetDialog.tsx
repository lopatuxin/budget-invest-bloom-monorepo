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
    symbol: '',
    name: '',
    shares: '',
    price: '',
    sector: ''
  });
  const { toast } = useToast();

  const sectors = [
    'Технологии',
    'Финансы', 
    'Здравоохранение',
    'Автомобили',
    'Потребительские товары',
    'Прочее'
  ];

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!formData.symbol || !formData.name || !formData.shares || !formData.price || !formData.sector) {
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
      symbol: formData.symbol.toUpperCase(),
      name: formData.name,
      shares: shares,
      price: price,
      value: shares * price,
      change: 0,
      changePercent: 0,
      sector: formData.sector
    };

    onAddAsset(newAsset);
    setFormData({ symbol: '', name: '', shares: '', price: '', sector: '' });
    setOpen(false);
    
    toast({
      title: "Успешно",
      description: `Актив ${formData.symbol} добавлен в портфель`
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
            <Label htmlFor="symbol">Символ (тикер)</Label>
            <Input
              id="symbol"
              placeholder="AAPL"
              value={formData.symbol}
              onChange={(e) => handleInputChange('symbol', e.target.value)}
              className="uppercase"
            />
          </div>
          
          <div className="space-y-2">
            <Label htmlFor="name">Название компании</Label>
            <Input
              id="name" 
              placeholder="Apple Inc."
              value={formData.name}
              onChange={(e) => handleInputChange('name', e.target.value)}
            />
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

          <div className="space-y-2">
            <Label htmlFor="sector">Сектор</Label>
            <Select value={formData.sector} onValueChange={(value) => handleInputChange('sector', value)}>
              <SelectTrigger>
                <SelectValue placeholder="Выберите сектор" />
              </SelectTrigger>
              <SelectContent>
                {sectors.map((sector) => (
                  <SelectItem key={sector} value={sector}>
                    {sector}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
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
const EMOJI_OPTIONS = ['🛒', '🍽️', '🏠', '🚗', '💊', '🎓', '🎮', '👕', '✈️', '💰', '📱', '🎬', '🐱', '💡', '🎁', '💇'];

interface Props {
  value: string;
  onChange: (emoji: string) => void;
}

export function CategoryEmojiPicker({ value, onChange }: Props) {
  return (
    <div className="grid grid-cols-8 gap-1.5">
      {EMOJI_OPTIONS.map((emoji) => (
        <button
          key={emoji}
          type="button"
          aria-label={`Выбрать эмодзи ${emoji}`}
          aria-pressed={value === emoji}
          onClick={() => onChange(value === emoji ? '' : emoji)}
          className={`w-9 h-9 rounded-lg text-lg flex items-center justify-center transition-all duration-150 ${
            value === emoji
              ? 'bg-white/15 ring-1 ring-white/30'
              : 'hover:bg-white/10'
          }`}
        >
          {emoji}
        </button>
      ))}
    </div>
  );
}

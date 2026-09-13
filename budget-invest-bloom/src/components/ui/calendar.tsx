import * as React from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { DayPicker } from "react-day-picker";

import { cn } from "@/lib/utils";

export type CalendarProps = React.ComponentProps<typeof DayPicker>;

// The selected look lives in aria-selected: variants on `day`: variant rules are emitted
// after plain utilities, so they beat the day's own text/background regardless of class order.
function Calendar({
  className,
  classNames,
  showOutsideDays = true,
  ...props
}: CalendarProps) {
  return (
    <DayPicker
      showOutsideDays={showOutsideDays}
      className={cn("p-3", className)}
      classNames={{
        months: "flex flex-col sm:flex-row space-y-4 sm:space-x-4 sm:space-y-0",
        month: "space-y-4",
        caption: "flex justify-center pt-1 relative items-center",
        caption_label: "text-sm font-medium text-app-text",
        nav: "space-x-1 flex items-center",
        nav_button:
          "inline-flex items-center justify-center h-7 w-7 rounded-md border border-app-border-strong bg-transparent p-0 text-app-text-muted hover:bg-app-surface-2 hover:text-app-text",
        nav_button_previous: "absolute left-1",
        nav_button_next: "absolute right-1",
        table: "w-full border-collapse space-y-1",
        head_row: "flex",
        head_cell: "text-app-text-dim rounded-md w-9 font-normal text-[0.8rem]",
        row: "flex w-full mt-2",
        cell: "h-9 w-9 text-center text-sm p-0 relative focus-within:relative focus-within:z-20",
        day: "h-9 w-9 p-0 rounded-md font-normal text-app-text hover:bg-app-surface-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-app-accent aria-selected:opacity-100 aria-selected:bg-app-accent aria-selected:text-app-accent-ink aria-selected:hover:bg-app-accent",
        day_selected: "font-medium",
        day_today: "bg-app-accent-soft",
        day_outside: "day-outside text-app-text-dim opacity-50 aria-selected:opacity-30",
        day_disabled: "text-app-text-dim opacity-50",
        day_hidden: "invisible",
        ...classNames,
      }}
      components={{
        IconLeft: ({ ..._props }) => <ChevronLeft className="h-4 w-4" />,
        IconRight: ({ ..._props }) => <ChevronRight className="h-4 w-4" />,
      }}
      {...props}
    />
  );
}
Calendar.displayName = "Calendar";

export { Calendar };

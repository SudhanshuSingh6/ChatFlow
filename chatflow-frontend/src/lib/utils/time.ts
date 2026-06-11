import { format, isToday, isYesterday, isThisWeek } from "date-fns";

/** Compact timestamp for list rows: "12:04", "Yesterday", "Tue", "03/14/25". */
export function formatListTime(iso: string | null): string {
  if (!iso) return "";
  const d = new Date(iso);
  if (isToday(d)) return format(d, "HH:mm");
  if (isYesterday(d)) return "Yesterday";
  if (isThisWeek(d)) return format(d, "EEE");
  return format(d, "dd/MM/yy");
}

/** "HH:mm" for message bubbles. */
export function formatMessageTime(iso: string): string {
  return format(new Date(iso), "HH:mm");
}

/** Day label for date dividers: "Today", "Yesterday", "March 14, 2025". */
export function formatDateDivider(iso: string): string {
  const d = new Date(iso);
  if (isToday(d)) return "Today";
  if (isYesterday(d)) return "Yesterday";
  return format(d, "MMMM d, yyyy");
}

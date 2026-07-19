export function calculateComingSunday(dateValue: string) {
  const date = new Date(`${dateValue}T00:00:00Z`);
  const day = date.getUTCDay();
  const daysUntilSunday = day === 0 ? 0 : 7 - day;
  date.setUTCDate(date.getUTCDate() + daysUntilSunday);
  return date.toISOString().slice(0, 10);
}

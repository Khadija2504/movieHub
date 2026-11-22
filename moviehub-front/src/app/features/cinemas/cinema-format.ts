import { ScreenType, ScreeningFormat, Showtime } from '../../core/models/cinema';

/** Display helpers for cinema data. Dates are "yyyy-MM-dd" strings local to the cinema. */

export const SCREEN_TYPE_LABELS: Record<ScreenType, string> = {
  IMAX: 'IMAX',
  FOUR_DX: '4DX',
  DOLBY: 'Dolby Atmos',
  STANDARD: 'Standard',
};

/** Badge colours per screen type (premium formats stand out). */
export const SCREEN_TYPE_BADGES: Record<ScreenType, string> = {
  IMAX: 'bg-sky-500/12 text-sky-300 ring-sky-500/25',
  FOUR_DX: 'bg-violet-500/12 text-violet-300 ring-violet-500/25',
  DOLBY: 'bg-accent/12 text-accent ring-accent/25',
  STANDARD: 'bg-white/5 text-muted ring-border',
};

export function formatLabel(format: ScreeningFormat): string {
  return format === 'THREE_D' ? '3D' : '2D';
}

/** "EN · FR subs" or "FR" */
export function versionLabel(showtime: Pick<Showtime, 'language' | 'subtitles'>): string {
  return showtime.subtitles ? `${showtime.language} · ${showtime.subtitles} subs` : showtime.language;
}

const languageNames = new Intl.DisplayNames(['en'], { type: 'language' });

/** "English with French subtitles" */
export function versionLongLabel(showtime: Pick<Showtime, 'language' | 'subtitles'>): string {
  const language = languageName(showtime.language);
  return showtime.subtitles ? `${language} with ${languageName(showtime.subtitles)} subtitles` : language;
}

function languageName(code: string): string {
  try {
    return languageNames.of(code.toLowerCase()) ?? code;
  } catch {
    return code;
  }
}

/** "2026-11-21T19:30:00" -> "19:30" */
export function timeOf(localDateTime: string): string {
  return localDateTime.slice(11, 16);
}

/** Today's date in an IANA time zone, as yyyy-MM-dd. */
export function todayIn(timeZone?: string): string {
  // en-CA formats dates as yyyy-MM-dd.
  return new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
}

/** Parses yyyy-MM-dd as a calendar date (no time zone shift). */
function calendarDate(date: string): Date {
  const [y, m, d] = date.split('-').map(Number);
  return new Date(y, m - 1, d);
}

function addDays(date: string, days: number): string {
  const d = calendarDate(date);
  d.setDate(d.getDate() + days);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** "Today", "Tomorrow" or the weekday ("Sat"). */
export function relativeDay(date: string, today: string): string {
  if (date === today) return 'Today';
  if (date === addDays(today, 1)) return 'Tomorrow';
  return calendarDate(date).toLocaleDateString('en', { weekday: 'short' });
}

/** "Saturday, November 21" */
export function longDate(date: string): string {
  return calendarDate(date.slice(0, 10)).toLocaleDateString('en', { weekday: 'long', month: 'long', day: 'numeric' });
}

/** { day: "21", month: "Nov" } */
export function dayParts(date: string): { day: string; month: string } {
  const d = calendarDate(date);
  return { day: String(d.getDate()), month: d.toLocaleDateString('en', { month: 'short' }) };
}

/** Link to the location on OpenStreetMap. */
export function mapLink(latitude: number, longitude: number): string {
  return `https://www.openstreetmap.org/?mlat=${latitude}&mlon=${longitude}#map=17/${latitude}/${longitude}`;
}

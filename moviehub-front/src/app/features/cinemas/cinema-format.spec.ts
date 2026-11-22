import { dayParts, formatLabel, longDate, relativeDay, timeOf, todayIn, versionLabel, versionLongLabel } from './cinema-format';

describe('cinema-format', () => {
  it('labels days relative to today', () => {
    expect(relativeDay('2026-11-21', '2026-11-21')).toBe('Today');
    expect(relativeDay('2026-11-22', '2026-11-21')).toBe('Tomorrow');
    expect(relativeDay('2026-11-23', '2026-11-21')).toBe('Mon');
    // Month and year boundaries
    expect(relativeDay('2027-01-01', '2026-12-31')).toBe('Tomorrow');
  });

  it('formats dates without time zone shifts', () => {
    expect(longDate('2026-11-21')).toBe('Saturday, November 21');
    expect(longDate('2026-11-21T23:30:00')).toBe('Saturday, November 21');
    expect(dayParts('2026-03-01')).toEqual({ day: '1', month: 'Mar' });
    expect(timeOf('2026-11-21T09:05:00')).toBe('09:05');
  });

  it('returns today as yyyy-MM-dd in a given zone', () => {
    expect(todayIn('Europe/Paris')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it('describes versions and formats', () => {
    expect(versionLabel({ language: 'EN', subtitles: 'FR' })).toBe('EN · FR subs');
    expect(versionLabel({ language: 'FR', subtitles: null })).toBe('FR');
    expect(versionLongLabel({ language: 'EN', subtitles: 'FR' })).toBe('English with French subtitles');
    expect(formatLabel('THREE_D')).toBe('3D');
  });
});

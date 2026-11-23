import { countdown, secondsUntil, shortDateTime } from './booking-format';

describe('booking-format', () => {
  it('formats a countdown as m:ss and never goes negative', () => {
    expect(countdown(600)).toBe('10:00');
    expect(countdown(65.9)).toBe('1:05');
    expect(countdown(9)).toBe('0:09');
    expect(countdown(-3)).toBe('0:00');
  });

  it('counts the seconds left until an instant', () => {
    const now = Date.parse('2026-11-23T10:00:00Z');
    expect(secondsUntil('2026-11-23T10:10:00Z', now)).toBe(600);
    expect(secondsUntil('2026-11-23T09:59:00Z', now)).toBe(-60);
    expect(secondsUntil(null, now)).toBe(0);
  });

  it('shows deadlines in the cinema zone', () => {
    expect(shortDateTime('2026-11-24T17:30:00Z', 'Europe/Paris')).toBe('Tue 24 Nov, 18:30');
    expect(shortDateTime('2026-11-24T17:30:00Z', 'Asia/Shanghai')).toBe('Wed 25 Nov, 01:30');
  });
});

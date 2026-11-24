import { clock, qualityName, timeLeft } from './streaming-format';

describe('streaming-format', () => {
  it('formats playback times', () => {
    expect(clock(0)).toBe('0:00');
    expect(clock(754.6)).toBe('12:34');
    expect(clock(3723)).toBe('1:02:03');
    expect(clock(-5)).toBe('0:00');
  });

  it('describes the time left on a rental', () => {
    const now = Date.parse('2026-11-24T10:00:00Z');
    expect(timeLeft('2026-11-26T10:00:00Z', now)).toBe('2 days left');
    expect(timeLeft('2026-11-25T14:00:00Z', now)).toBe('1 day 4 h left');
    expect(timeLeft('2026-11-24T13:20:00Z', now)).toBe('3 h 20 min left');
    expect(timeLeft('2026-11-24T10:12:30Z', now)).toBe('12 min left');
    expect(timeLeft('2026-11-24T09:00:00Z', now)).toBe('Ended');
    expect(timeLeft(null, now)).toBe('');
  });

  it('names plan qualities', () => {
    expect(qualityName(480)).toBe('SD (480p)');
    expect(qualityName(720)).toBe('HD (720p)');
    expect(qualityName(1080)).toBe('Full HD (1080p)');
  });
});

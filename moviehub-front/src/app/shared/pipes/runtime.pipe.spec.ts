import { RuntimePipe } from './runtime.pipe';

describe('RuntimePipe', () => {
  const pipe = new RuntimePipe();

  it('formats hours and minutes', () => {
    expect(pipe.transform(135)).toBe('2h 15m');
    expect(pipe.transform(120)).toBe('2h 0m');
    expect(pipe.transform(45)).toBe('45m');
  });

  it('returns an empty string for missing values', () => {
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform(0)).toBe('');
  });
});

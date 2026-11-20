import { passwordScore } from './register-page';

describe('passwordScore', () => {
  it('is 0 for an empty password', () => {
    expect(passwordScore('')).toBe(0);
  });

  it('grows with length, digits, mixed case and symbols', () => {
    expect(passwordScore('abc')).toBe(1);
    expect(passwordScore('abcdefgh1')).toBe(2);
    expect(passwordScore('Abcdefgh1')).toBe(3);
    expect(passwordScore('Abcdefgh1!')).toBe(4);
  });
});

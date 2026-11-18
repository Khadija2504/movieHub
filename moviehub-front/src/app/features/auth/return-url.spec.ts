import { safeReturnUrl } from './return-url';

describe('safeReturnUrl', () => {
  it('keeps in-app paths', () => {
    expect(safeReturnUrl('/account/bookings?x=1')).toBe('/account/bookings?x=1');
  });

  it.each([undefined, null, '', 'https://evil.com', '//evil.com', '/\\evil.com', 'account'])(
    'rejects %s',
    (url) => {
      expect(safeReturnUrl(url)).toBeNull();
    },
  );
});

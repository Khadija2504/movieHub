/**
 * Only allow in-app paths as post-login redirects ("/account/..."), never absolute or
 * protocol-relative URLs ("https://evil.com", "//evil.com"), to prevent open redirects.
 */
export function safeReturnUrl(url: string | undefined | null): string | null {
  if (!url || !url.startsWith('/') || url.startsWith('//') || url.startsWith('/\\')) {
    return null;
  }
  return url;
}

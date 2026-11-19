// Production build. The API is served behind the same origin (reverse proxy /api -> backend).
export const environment = {
  production: true,
  apiUrl: '/api',
  tmdbImageUrl: 'https://image.tmdb.org/t/p',
  /** ISO 4217 code used to display prices. */
  currency: 'EUR',
};

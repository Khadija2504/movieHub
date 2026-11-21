import { Pipe, PipeTransform } from '@angular/core';

import { environment } from '../../../environments/environment';

/** Origin of the API ("http://localhost:8080" in dev, "" when served behind the same origin). */
const API_ORIGIN = environment.apiUrl.replace(/\/api\/?$/, '');

/** Backend-relative file URLs ("/api/files/...") -> absolute; absolute URLs are returned unchanged. */
export function assetUrl(url: string | null | undefined): string | null {
  if (!url) return null;
  return url.startsWith('/') ? API_ORIGIN + url : url;
}

@Pipe({ name: 'assetUrl' })
export class AssetUrlPipe implements PipeTransform {
  transform(url: string | null | undefined): string | null {
    return assetUrl(url);
  }
}

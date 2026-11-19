import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { CatalogService } from './catalog.service';

describe('CatalogService', () => {
  const api = environment.apiUrl;
  let service: CatalogService;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(CatalogService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('sends only the filters that are set', () => {
    service.browse('SERIES', { genre: 18, sort: 'TOP_RATED', year: undefined, page: 2 }).subscribe();

    const req = backend.expectOne((r) => r.url === `${api}/series`);
    expect(req.request.params.keys().sort()).toEqual(['genre', 'page', 'sort']);
    expect(req.request.params.get('genre')).toBe('18');
    req.flush({ content: [], page: 2, totalPages: 0, totalResults: 0 });
  });

  it('fetches each genre list only once', () => {
    service.genres('MOVIE').subscribe();
    service.genres('MOVIE').subscribe();

    backend.expectOne(`${api}/genres?type=MOVIE`).flush([{ id: 28, name: 'Action' }]);
    service.genres('MOVIE').subscribe((genres) => expect(genres[0].name).toBe('Action'));
    backend.expectNone(`${api}/genres?type=MOVIE`);
  });
});

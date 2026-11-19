/** Mirrors com.moviehub.catalog.dto.CatalogDtos. All `id`s are TMDB ids. */

export type MediaType = 'MOVIE' | 'SERIES';
export type SortOption = 'POPULAR' | 'TOP_RATED' | 'NEWEST' | 'TITLE';

export interface Page<T> {
  content: T[];
  /** 1-based */
  page: number;
  totalPages: number;
  totalResults: number;
}

export interface MediaSummary {
  id: number;
  mediaType: MediaType;
  title: string;
  overview: string | null;
  posterUrl: string | null;
  backdropUrl: string | null;
  releaseDate: string | null;
  year: number | null;
  rating: number | null;
  voteCount: number | null;
  genreIds: number[];
}

export interface Genre {
  id: number;
  name: string;
}

export interface Person {
  id: number;
  name: string;
  role: string | null;
  profileUrl: string | null;
}

export interface Trailer {
  key: string;
  name: string;
  watchUrl: string;
  embedUrl: string;
}

export interface MovieOffer {
  inCatalog: boolean;
  streamable: boolean;
  purchasePrice: number | null;
  rentalPrice: number | null;
}

export interface SeriesOffer {
  inCatalog: boolean;
  streamable: boolean;
}

interface TitleDetails {
  id: number;
  title: string;
  tagline: string | null;
  overview: string | null;
  posterUrl: string | null;
  backdropUrl: string | null;
  year: number | null;
  status: string | null;
  rating: number | null;
  voteCount: number | null;
  genres: Genre[];
  cast: Person[];
  trailer: Trailer | null;
  recommendations: MediaSummary[];
}

export interface MovieDetails extends TitleDetails {
  releaseDate: string | null;
  runtime: number | null;
  originalLanguage: string | null;
  directors: Person[];
  offer: MovieOffer;
}

export interface SeasonSummary {
  seasonNumber: number;
  name: string;
  overview: string | null;
  episodeCount: number | null;
  airDate: string | null;
  posterUrl: string | null;
}

export interface SeriesDetails extends TitleDetails {
  firstAirDate: string | null;
  lastAirDate: string | null;
  numberOfSeasons: number | null;
  numberOfEpisodes: number | null;
  episodeRuntime: number | null;
  creators: Person[];
  seasons: SeasonSummary[];
  offer: SeriesOffer;
}

export interface Episode {
  episodeNumber: number;
  name: string;
  overview: string | null;
  airDate: string | null;
  runtime: number | null;
  stillUrl: string | null;
  rating: number | null;
}

export interface SeasonDetails {
  seriesId: number;
  seasonNumber: number;
  name: string;
  overview: string | null;
  airDate: string | null;
  posterUrl: string | null;
  episodes: Episode[];
}

export interface BrowseFilter {
  genre?: number;
  year?: number;
  minRating?: number;
  sort?: SortOption;
  page?: number;
}

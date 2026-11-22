/**
 * Mirrors com.moviehub.cinema.CinemaDtos. Movie ids (`tmdbId`) are TMDB ids.
 * `localStart` / `localEnd` are wall-clock times in the cinema's time zone ("2026-11-21T19:30:00"):
 * display those, never convert them to the browser's zone.
 */

export type ScreenType = 'IMAX' | 'FOUR_DX' | 'DOLBY' | 'STANDARD';
export type SeatType = 'STANDARD' | 'VIP' | 'ACCESSIBLE';
export type SeatStatus = 'AVAILABLE' | 'TAKEN';
export type ScreeningFormat = 'TWO_D' | 'THREE_D';
export type ScreeningStatus = 'SCHEDULED' | 'CANCELLED';

export interface CinemaSummary {
  id: number;
  name: string;
  city: string;
  address: string;
  postalCode: string | null;
  latitude: number | null;
  longitude: number | null;
  roomCount: number;
  seatCount: number;
  screenTypes: ScreenType[];
}

export interface RoomSummary {
  id: number;
  name: string;
  screenType: ScreenType;
  capacity: number;
  active: boolean;
}

export interface CinemaDetails {
  id: number;
  name: string;
  city: string;
  address: string;
  postalCode: string | null;
  phone: string | null;
  description: string | null;
  latitude: number | null;
  longitude: number | null;
  timeZone: string;
  active: boolean;
  rooms: RoomSummary[];
}

export interface CinemaRef {
  id: number;
  name: string;
  city: string;
  address: string;
  latitude: number | null;
  longitude: number | null;
  timeZone: string;
}

export interface ScreeningMovie {
  tmdbId: number;
  title: string;
  posterUrl: string | null;
  backdropUrl: string | null;
  runtime: number | null;
  rating: number | null;
  year: number | null;
}

export interface Showtime {
  id: number;
  startsAt: string;
  localStart: string;
  format: ScreeningFormat;
  language: string;
  subtitles: string | null;
  roomId: number;
  roomName: string;
  screenType: ScreenType;
  price: number;
  seatsLeft: number;
}

export interface MovieShowtimes {
  movie: ScreeningMovie;
  showtimes: Showtime[];
}

export interface CinemaSchedule {
  /** yyyy-MM-dd */
  date: string;
  /** Upcoming days that have showtimes. */
  dates: string[];
  movies: MovieShowtimes[];
}

export interface CinemaShowtimes {
  cinema: CinemaRef;
  showtimes: Showtime[];
}

export interface MovieSchedule {
  date: string;
  dates: string[];
  cities: string[];
  cinemas: CinemaShowtimes[];
}

export interface ScreeningDetails {
  id: number;
  startsAt: string;
  endsAt: string;
  localStart: string;
  localEnd: string;
  format: ScreeningFormat;
  language: string;
  subtitles: string | null;
  price: number;
  vipPrice: number;
  status: ScreeningStatus;
  bookable: boolean;
  movie: ScreeningMovie;
  cinema: CinemaRef;
  room: RoomSummary;
}

export interface SeatView {
  id: number;
  label: string;
  row: string;
  number: number;
  gridRow: number;
  gridColumn: number;
  type: SeatType;
  status: SeatStatus;
}

export interface SeatMap {
  screeningId: number;
  rows: number;
  columns: number;
  capacity: number;
  available: number;
  seats: SeatView[];
}

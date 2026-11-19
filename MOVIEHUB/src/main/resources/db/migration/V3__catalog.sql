-- Local catalog: only titles imported by an admin. Metadata (cast, trailers, ...) is always read live from TMDB;
-- these tables hold the MovieHub-specific data (availability, prices, video) and are referenced by
-- watchlists, reviews, purchases and screenings in later phases.

CREATE TABLE movies
(
    id             BIGSERIAL PRIMARY KEY,
    tmdb_id        INTEGER          NOT NULL UNIQUE,
    title          VARCHAR(255)     NOT NULL,
    overview       TEXT,
    poster_path    VARCHAR(255),
    backdrop_path  VARCHAR(255),
    release_date   DATE,
    runtime        INTEGER,
    vote_average   DOUBLE PRECISION,
    streamable     BOOLEAN          NOT NULL DEFAULT FALSE,
    purchase_price NUMERIC(10, 2) CHECK (purchase_price IS NULL OR purchase_price >= 0),
    rental_price   NUMERIC(10, 2) CHECK (rental_price IS NULL OR rental_price >= 0),
    video_url      VARCHAR(500),
    active         BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE TABLE series
(
    id                BIGSERIAL PRIMARY KEY,
    tmdb_id           INTEGER          NOT NULL UNIQUE,
    title             VARCHAR(255)     NOT NULL,
    overview          TEXT,
    poster_path       VARCHAR(255),
    backdrop_path     VARCHAR(255),
    first_air_date    DATE,
    number_of_seasons INTEGER,
    vote_average      DOUBLE PRECISION,
    streamable        BOOLEAN          NOT NULL DEFAULT FALSE,
    active            BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ      NOT NULL DEFAULT now()
);

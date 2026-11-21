-- Titles are referenced by (media_type, tmdb_id), so any TMDB title can be saved or reviewed,
-- not only the ones imported into the local catalog. title/poster_path are snapshots for list pages.

CREATE TABLE watchlist_items
(
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    media_type   VARCHAR(10)  NOT NULL CHECK (media_type IN ('MOVIE', 'SERIES')),
    tmdb_id      INTEGER      NOT NULL,
    title        VARCHAR(255) NOT NULL,
    poster_path  VARCHAR(255),
    release_year INTEGER,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_watchlist_user_title UNIQUE (user_id, media_type, tmdb_id)
);

CREATE INDEX idx_watchlist_user_created ON watchlist_items (user_id, created_at DESC);

CREATE TABLE reviews
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    media_type  VARCHAR(10)  NOT NULL CHECK (media_type IN ('MOVIE', 'SERIES')),
    tmdb_id     INTEGER      NOT NULL,
    title       VARCHAR(255) NOT NULL,
    poster_path VARCHAR(255),
    rating      SMALLINT     NOT NULL CHECK (rating BETWEEN 1 AND 10),
    comment     TEXT CHECK (comment IS NULL OR length(comment) <= 2000),
    -- HIDDEN = removed by a moderator (admin review management, later phase)
    status      VARCHAR(10)  NOT NULL DEFAULT 'VISIBLE' CHECK (status IN ('VISIBLE', 'HIDDEN')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_review_user_title UNIQUE (user_id, media_type, tmdb_id)
);

CREATE INDEX idx_reviews_title ON reviews (media_type, tmdb_id, status, created_at DESC);
CREATE INDEX idx_reviews_user_created ON reviews (user_id, created_at DESC);

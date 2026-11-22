-- Cinemas, their rooms (with a fixed seat layout) and screenings of catalog movies in those rooms.

CREATE TABLE cinemas
(
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(120)  NOT NULL,
    city        VARCHAR(80)   NOT NULL,
    address     VARCHAR(255)  NOT NULL,
    postal_code VARCHAR(20),
    phone       VARCHAR(30),
    description TEXT,
    -- Optional coordinates for the map.
    latitude    DOUBLE PRECISION CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    longitude   DOUBLE PRECISION CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    -- IANA zone (e.g. Europe/Paris): showtimes are entered and displayed in the cinema's local time.
    time_zone   VARCHAR(50)   NOT NULL,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_cinemas_city ON cinemas (lower(city));

CREATE TABLE rooms
(
    id          BIGSERIAL PRIMARY KEY,
    cinema_id   BIGINT       NOT NULL REFERENCES cinemas (id) ON DELETE CASCADE,
    name        VARCHAR(60)  NOT NULL,
    screen_type VARCHAR(20)  NOT NULL CHECK (screen_type IN ('STANDARD', 'IMAX', 'DOLBY', 'FOUR_DX')),
    -- Grid size of the seat map (rows x columns, columns include aisles).
    row_count   SMALLINT     NOT NULL CHECK (row_count BETWEEN 1 AND 26),
    column_count SMALLINT    NOT NULL CHECK (column_count BETWEEN 1 AND 50),
    capacity    INTEGER      NOT NULL CHECK (capacity > 0),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_rooms_cinema_name UNIQUE (cinema_id, name)
);

CREATE TABLE seats
(
    id           BIGSERIAL PRIMARY KEY,
    room_id      BIGINT      NOT NULL REFERENCES rooms (id) ON DELETE CASCADE,
    row_label    VARCHAR(2)  NOT NULL,
    seat_number  SMALLINT    NOT NULL CHECK (seat_number > 0),
    -- Position on the seat map grid (0-based); aisles are the empty columns.
    grid_row     SMALLINT    NOT NULL CHECK (grid_row >= 0),
    grid_column  SMALLINT    NOT NULL CHECK (grid_column >= 0),
    seat_type    VARCHAR(20) NOT NULL CHECK (seat_type IN ('STANDARD', 'VIP', 'ACCESSIBLE')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_seats_label UNIQUE (room_id, row_label, seat_number),
    CONSTRAINT uq_seats_position UNIQUE (room_id, grid_row, grid_column)
);

CREATE TABLE screenings
(
    id          BIGSERIAL PRIMARY KEY,
    room_id     BIGINT         NOT NULL REFERENCES rooms (id),
    movie_id    BIGINT         NOT NULL REFERENCES movies (id),
    starts_at   TIMESTAMPTZ    NOT NULL,
    -- starts_at + runtime + cleaning time: used to prevent overlapping screenings in a room.
    ends_at     TIMESTAMPTZ    NOT NULL,
    format      VARCHAR(10)    NOT NULL CHECK (format IN ('TWO_D', 'THREE_D')),
    language    VARCHAR(10)    NOT NULL,
    subtitles   VARCHAR(10),
    price       NUMERIC(6, 2)  NOT NULL CHECK (price >= 0),
    vip_price   NUMERIC(6, 2)  NOT NULL CHECK (vip_price >= 0),
    status      VARCHAR(20)    NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_screenings_time CHECK (ends_at > starts_at)
);

CREATE INDEX idx_screenings_room_time ON screenings (room_id, starts_at);
CREATE INDEX idx_screenings_movie_time ON screenings (movie_id, starts_at);
CREATE INDEX idx_screenings_starts_at ON screenings (starts_at);

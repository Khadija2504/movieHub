-- Cinema bookings: a booking holds seats of one screening for a short time (PENDING) until it is paid (CONFIRMED).

CREATE TABLE bookings
(
    id               BIGSERIAL PRIMARY KEY,
    -- Short code printed on the ticket (e.g. MH-7K3P9Q).
    reference        VARCHAR(12)   NOT NULL UNIQUE,
    -- Kept (anonymised) when the account is deleted: sales history stays correct.
    user_id          BIGINT        REFERENCES users (id) ON DELETE SET NULL,
    screening_id     BIGINT        NOT NULL REFERENCES screenings (id),
    status           VARCHAR(20)   NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    total_amount     NUMERIC(8, 2) NOT NULL CHECK (total_amount >= 0),
    currency         VARCHAR(3)    NOT NULL,
    -- End of the seat hold while PENDING.
    expires_at       TIMESTAMPTZ   NOT NULL,
    payment_provider VARCHAR(20) CHECK (payment_provider IN ('STRIPE', 'DEMO')),
    -- Stripe PaymentIntent id (pi_...) or a demo payment id.
    payment_id       VARCHAR(100),
    paid_at          TIMESTAMPTZ,
    cancelled_at     TIMESTAMPTZ,
    refund_id        VARCHAR(100),
    checked_in_at    TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_bookings_user_created ON bookings (user_id, created_at DESC);
CREATE INDEX idx_bookings_screening ON bookings (screening_id);
CREATE INDEX idx_bookings_pending_expiry ON bookings (expires_at) WHERE status = 'PENDING';
CREATE UNIQUE INDEX uq_bookings_payment ON bookings (payment_id) WHERE payment_id IS NOT NULL;

CREATE TABLE booking_seats
(
    id           BIGSERIAL PRIMARY KEY,
    booking_id   BIGINT        NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    screening_id BIGINT        NOT NULL REFERENCES screenings (id),
    seat_id      BIGINT        NOT NULL REFERENCES seats (id),
    -- Snapshots: what was sold, even if the room layout changes later.
    seat_label   VARCHAR(5)    NOT NULL,
    seat_type    VARCHAR(20)   NOT NULL CHECK (seat_type IN ('STANDARD', 'VIP', 'ACCESSIBLE')),
    price        NUMERIC(6, 2) NOT NULL CHECK (price >= 0),
    -- Set to false when the booking is cancelled or expires: the seat is free again.
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- A seat can be held or sold only once per screening: the database is the final guard against double booking.
CREATE UNIQUE INDEX uq_booking_seats_active ON booking_seats (screening_id, seat_id) WHERE active;
CREATE INDEX idx_booking_seats_booking ON booking_seats (booking_id);

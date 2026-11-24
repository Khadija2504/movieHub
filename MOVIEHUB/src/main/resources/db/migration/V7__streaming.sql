-- Streaming: video sources, subscription plans, store orders (purchases, rentals, subscription payments),
-- the user's library and watch progress.

-- ---------------------------------------------------------------- catalog offer

ALTER TABLE movies
    -- false = buy or rent only (e.g. new releases), even for subscribers.
    ADD COLUMN in_subscription BOOLEAN NOT NULL DEFAULT TRUE,
    -- Licence / credit line shown in the player (e.g. "(CC) Blender Foundation, CC BY 3.0").
    ADD COLUMN video_credit    VARCHAR(255);

-- A movie can have several files: one adaptive HLS stream (quality NULL) and/or MP4 files of fixed heights.
CREATE TABLE movie_video_sources
(
    id         BIGSERIAL PRIMARY KEY,
    movie_id   BIGINT       NOT NULL REFERENCES movies (id) ON DELETE CASCADE,
    -- https://... (MP4 or .m3u8) or local:<path under app.streaming.videos-dir>
    url        VARCHAR(500) NOT NULL,
    -- Picture height (480, 720, 1080) of an MP4; NULL for an adaptive HLS stream.
    quality    INTEGER CHECK (quality IS NULL OR quality BETWEEN 100 AND 4320),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_video_sources_movie ON movie_video_sources (movie_id);

-- The single video_url of V3 becomes a source.
INSERT INTO movie_video_sources (movie_id, url, quality)
SELECT id, video_url, NULL
FROM movies
WHERE video_url IS NOT NULL
  AND video_url <> '';

ALTER TABLE movies DROP COLUMN video_url;

-- ---------------------------------------------------------------- subscriptions

CREATE TABLE subscription_plans
(
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(20)   NOT NULL UNIQUE,
    name        VARCHAR(50)   NOT NULL,
    description VARCHAR(255),
    price       NUMERIC(8, 2) NOT NULL CHECK (price > 0),
    period_days INTEGER       NOT NULL CHECK (period_days > 0),
    -- Highest picture height streamed with this plan.
    max_quality INTEGER       NOT NULL,
    screens     INTEGER       NOT NULL DEFAULT 1,
    features    TEXT[]        NOT NULL DEFAULT '{}',
    sort_order  INTEGER       NOT NULL DEFAULT 0,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

INSERT INTO subscription_plans (code, name, description, price, period_days, max_quality, screens, features, sort_order)
VALUES ('BASIC', 'Basic', 'The whole streaming catalog on one screen.', 4.99, 30, 480, 1,
        ARRAY ['Every title included with a subscription', 'SD quality (480p)', 'Watch on 1 screen', 'Cancel anytime'],
        1),
       ('STANDARD', 'Standard', 'Sharp HD for the whole household.', 8.99, 30, 720, 2,
        ARRAY ['Every title included with a subscription', 'HD quality (720p)', 'Watch on 2 screens', 'Cancel anytime'],
        2),
       ('PREMIUM', 'Premium', 'The best picture MovieHub offers.', 12.99, 30, 1080, 4,
        ARRAY ['Every title included with a subscription', 'Full HD quality (1080p)', 'Watch on 4 screens',
            'Cancel anytime'],
        3);

-- Stripe customer that holds the saved card used for automatic renewals.
ALTER TABLE users
    ADD COLUMN payment_customer_id VARCHAR(100);

CREATE TABLE user_subscriptions
(
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    plan_id              BIGINT      NOT NULL REFERENCES subscription_plans (id),
    -- Plan chosen for the next period (plan change), NULL = same plan.
    next_plan_id         BIGINT REFERENCES subscription_plans (id),
    status               VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'EXPIRED')),
    started_at           TIMESTAMPTZ NOT NULL,
    current_period_start TIMESTAMPTZ NOT NULL,
    current_period_end   TIMESTAMPTZ NOT NULL,
    auto_renew           BOOLEAN     NOT NULL DEFAULT TRUE,
    cancelled_at         TIMESTAMPTZ,
    ended_at             TIMESTAMPTZ,
    -- Why it ended: CANCELLED (auto-renew off) or PAYMENT_FAILED.
    end_reason           VARCHAR(20) CHECK (end_reason IN ('CANCELLED', 'PAYMENT_FAILED')),
    payment_provider     VARCHAR(20) CHECK (payment_provider IN ('STRIPE', 'DEMO')),
    -- Saved card (Stripe pm_...) charged on renewal.
    payment_method_id    VARCHAR(100),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- At most one running subscription per user.
CREATE UNIQUE INDEX uq_user_subscriptions_active ON user_subscriptions (user_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_user_subscriptions_renewal ON user_subscriptions (current_period_end) WHERE status = 'ACTIVE';
CREATE INDEX idx_user_subscriptions_user ON user_subscriptions (user_id, created_at DESC);

-- ---------------------------------------------------------------- store orders

CREATE TABLE store_orders
(
    id               BIGSERIAL PRIMARY KEY,
    reference        VARCHAR(12)   NOT NULL UNIQUE,
    -- Kept (anonymised) when the account is deleted: sales history stays correct.
    user_id          BIGINT REFERENCES users (id) ON DELETE SET NULL,
    kind             VARCHAR(20)   NOT NULL CHECK (kind IN ('PURCHASE', 'RENTAL', 'SUBSCRIPTION')),
    movie_id         BIGINT REFERENCES movies (id),
    plan_id          BIGINT REFERENCES subscription_plans (id),
    -- Renewal payments of an existing subscription.
    subscription_id  BIGINT REFERENCES user_subscriptions (id) ON DELETE SET NULL,
    -- What was sold, as shown on receipts ("Sintel - 48-hour rental").
    description      VARCHAR(255)  NOT NULL,
    status           VARCHAR(20)   NOT NULL CHECK (status IN ('PENDING', 'PAID', 'CANCELLED', 'EXPIRED', 'FAILED',
                                                               'REFUNDED')),
    amount           NUMERIC(8, 2) NOT NULL CHECK (amount >= 0),
    currency         VARCHAR(3)    NOT NULL,
    -- Deadline to pay while PENDING.
    expires_at       TIMESTAMPTZ   NOT NULL,
    payment_provider VARCHAR(20) CHECK (payment_provider IN ('STRIPE', 'DEMO')),
    payment_id       VARCHAR(100),
    paid_at          TIMESTAMPTZ,
    ended_at         TIMESTAMPTZ,
    refund_id        VARCHAR(100),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_store_orders_target CHECK (
        (kind IN ('PURCHASE', 'RENTAL') AND movie_id IS NOT NULL)
            OR (kind = 'SUBSCRIPTION' AND plan_id IS NOT NULL))
);

CREATE INDEX idx_store_orders_user_created ON store_orders (user_id, created_at DESC);
CREATE INDEX idx_store_orders_pending_expiry ON store_orders (expires_at) WHERE status = 'PENDING';
CREATE UNIQUE INDEX uq_store_orders_payment ON store_orders (payment_id) WHERE payment_id IS NOT NULL;

-- ---------------------------------------------------------------- library

-- One row per user and movie: a rental (expires_at set) can later become a purchase (expires_at NULL).
CREATE TABLE library_items
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    movie_id    BIGINT      NOT NULL REFERENCES movies (id) ON DELETE CASCADE,
    kind        VARCHAR(20) NOT NULL CHECK (kind IN ('PURCHASE', 'RENTAL')),
    order_id    BIGINT REFERENCES store_orders (id) ON DELETE SET NULL,
    acquired_at TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_library_user_movie UNIQUE (user_id, movie_id),
    CONSTRAINT ck_library_expiry CHECK ((kind = 'PURCHASE') = (expires_at IS NULL))
);

CREATE INDEX idx_library_user_acquired ON library_items (user_id, acquired_at DESC);

-- ---------------------------------------------------------------- watch progress

CREATE TABLE watch_progress
(
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    movie_id         BIGINT      NOT NULL REFERENCES movies (id) ON DELETE CASCADE,
    position_seconds INTEGER     NOT NULL CHECK (position_seconds >= 0),
    duration_seconds INTEGER     NOT NULL CHECK (duration_seconds > 0),
    completed        BOOLEAN     NOT NULL DEFAULT FALSE,
    last_watched_at  TIMESTAMPTZ NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_watch_progress_user_movie UNIQUE (user_id, movie_id)
);

CREATE INDEX idx_watch_progress_user_recent ON watch_progress (user_id, last_watched_at DESC);

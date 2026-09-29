-- Runs on every start; every statement is safe to repeat.
-- Upgrading from the first version (course_id was text)? Reset the database once: docker compose down -v

CREATE TABLE IF NOT EXISTS app_user (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(254) NOT NULL UNIQUE,
    name          VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Accounts created by the first version had no role column.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'USER';

CREATE TABLE IF NOT EXISTS course (
    id          BIGSERIAL PRIMARY KEY,
    slug        VARCHAR(80)  NOT NULL UNIQUE,
    title       VARCHAR(160) NOT NULL,
    subtitle    VARCHAR(300) NOT NULL DEFAULT '',
    description TEXT         NOT NULL DEFAULT '',
    outcomes    TEXT         NOT NULL DEFAULT '',
    level       VARCHAR(20)  NOT NULL DEFAULT 'Beginner',
    price_inr   INTEGER      NOT NULL DEFAULT 0,
    published   BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS course_section (
    id         BIGSERIAL PRIMARY KEY,
    course_id  BIGINT       NOT NULL REFERENCES course (id) ON DELETE CASCADE,
    sort_order INTEGER      NOT NULL,
    title      VARCHAR(160) NOT NULL
);

CREATE TABLE IF NOT EXISTS lecture (
    id           BIGSERIAL PRIMARY KEY,
    section_id   BIGINT       NOT NULL REFERENCES course_section (id) ON DELETE CASCADE,
    sort_order   INTEGER      NOT NULL,
    title        VARCHAR(160) NOT NULL,
    duration_min INTEGER      NOT NULL DEFAULT 5,
    free_preview BOOLEAN      NOT NULL DEFAULT FALSE,
    video_url    VARCHAR(500),
    content      TEXT         NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS enrollment (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT      NOT NULL REFERENCES app_user (id),
    course_id           BIGINT      NOT NULL REFERENCES course (id),
    amount_paise        INTEGER     NOT NULL,
    currency            VARCHAR(3)  NOT NULL DEFAULT 'INR',
    razorpay_order_id   VARCHAR(60) NOT NULL UNIQUE,
    razorpay_payment_id VARCHAR(60),
    status              VARCHAR(20) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    paid_at             TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_enrollment_user ON enrollment (user_id, status);

CREATE TABLE IF NOT EXISTS lecture_progress (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES app_user (id),
    lecture_id   BIGINT      NOT NULL REFERENCES lecture (id) ON DELETE CASCADE,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, lecture_id)
);

CREATE TABLE IF NOT EXISTS password_reset (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash CHAR(64)    NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_password_reset_user ON password_reset (user_id, created_at);

-- Two-factor sign-in and session revocation
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS totp_secret VARCHAR(64);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS totp_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS totp_last_step BIGINT NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS token_version INT NOT NULL DEFAULT 0;

-- Course trailer video (a public intro on the course page)
ALTER TABLE course ADD COLUMN IF NOT EXISTS trailer_url VARCHAR(500);

-- Free-lesson progress for signed-in learners (synced from the browser)
CREATE TABLE IF NOT EXISTS lesson_progress (
    user_id      BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    lesson_id    VARCHAR(80) NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, lesson_id)
);

-- Study time per day (the learner's local date), used for pace and finish-date estimates
CREATE TABLE IF NOT EXISTS study_day (
    user_id BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    day     DATE   NOT NULL,
    seconds INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, day)
);

CREATE TABLE IF NOT EXISTS learning_goal (
    user_id         BIGINT PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    minutes_per_day INT         NOT NULL DEFAULT 30,
    days_per_week   INT         NOT NULL DEFAULT 5,
    path_id         VARCHAR(80),
    target_date     DATE,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- First-party, cookie-free analytics. "visitor" is an anonymous id that changes every day
-- (a hash of IP + browser + a daily random salt that is deleted after two days). No IP addresses are stored.
CREATE TABLE IF NOT EXISTS analytics_event (
    id       BIGSERIAL PRIMARY KEY,
    ts       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    visitor  CHAR(16)     NOT NULL,
    type     VARCHAR(24)  NOT NULL,
    path     VARCHAR(300),
    ref      VARCHAR(120),
    query    VARCHAR(120),
    results  INT,
    source   VARCHAR(60),
    campaign VARCHAR(80),
    device   VARCHAR(10),
    country  CHAR(2)
);
CREATE INDEX IF NOT EXISTS idx_analytics_ts ON analytics_event (ts);
CREATE INDEX IF NOT EXISTS idx_analytics_type_ts ON analytics_event (type, ts);

CREATE TABLE IF NOT EXISTS analytics_salt (
    day  DATE PRIMARY KEY,
    salt CHAR(64) NOT NULL
);

-- Feedback from visitors: general messages and quick "was this lesson helpful?" answers
CREATE TABLE IF NOT EXISTS feedback (
    id          BIGSERIAL PRIMARY KEY,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    type        VARCHAR(16)  NOT NULL,
    rating      SMALLINT,
    helpful     BOOLEAN,
    reasons     VARCHAR(200),
    message     VARCHAR(2000),
    name        VARCHAR(80),
    email       VARCHAR(254),
    page        VARCHAR(300),
    lesson_id   VARCHAR(80),
    device      VARCHAR(10),
    status      VARCHAR(16)  NOT NULL DEFAULT 'NEW',
    admin_note  VARCHAR(2000)
);
CREATE INDEX IF NOT EXISTS idx_feedback_status ON feedback (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_feedback_lesson ON feedback (lesson_id) WHERE lesson_id IS NOT NULL;

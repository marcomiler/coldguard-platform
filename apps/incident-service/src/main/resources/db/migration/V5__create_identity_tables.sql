CREATE TABLE identity.user_account (
    id               UUID PRIMARY KEY,
    username         VARCHAR(60)  NOT NULL,
    email            VARCHAR(254) NOT NULL,
    display_name     VARCHAR(120) NOT NULL,
    password_hash    VARCHAR(200) NOT NULL,
    enabled          BOOLEAN      NOT NULL,
    failed_attempts  INT          NOT NULL DEFAULT 0,
    locked_until     TIMESTAMPTZ  NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    version          BIGINT       NOT NULL
);

CREATE UNIQUE INDEX ux_user_account_username ON identity.user_account (lower(username));
CREATE UNIQUE INDEX ux_user_account_email    ON identity.user_account (lower(email));

CREATE TABLE identity.user_role (
    user_id     UUID         NOT NULL REFERENCES identity.user_account (id),
    role        VARCHAR(40)  NOT NULL,
    assigned_at TIMESTAMPTZ  NOT NULL,
    assigned_by VARCHAR(100) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE INDEX ix_user_role_role ON identity.user_role (role);

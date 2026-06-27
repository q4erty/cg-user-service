CREATE TABLE users
(
    id            UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    keycloak_id   VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    display_name  VARCHAR(255),
    avatar_url    VARCHAR(1024),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_login_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_users_keycloak_id ON users (keycloak_id);
CREATE UNIQUE INDEX uq_users_email ON users (lower(email));

CREATE INDEX idx_users_email ON users (email);

CREATE OR REPLACE FUNCTION update_updated_at_column()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE
    ON users
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

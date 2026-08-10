CREATE TABLE IF NOT EXISTS {{table:role_permissions}} (
    id BIGSERIAL PRIMARY KEY,
    guild_uuid VARCHAR(46) NOT NULL,
    role VARCHAR(20) NOT NULL,
    block_breaking BOOLEAN NOT NULL DEFAULT FALSE,
    block_placing BOOLEAN NOT NULL DEFAULT FALSE,
    interacting BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_role_permissions_guild_role UNIQUE (guild_uuid, role)
);

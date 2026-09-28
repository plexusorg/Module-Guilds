CREATE TABLE IF NOT EXISTS {{table:guilds}} (
    guild_uuid VARCHAR(46) NOT NULL PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    prefix TEXT,
    prefix_key VARCHAR(5) UNIQUE,
    owner_uuid VARCHAR(46) NOT NULL,
    created_at BIGINT NOT NULL,
    world_access VARCHAR(12) NOT NULL DEFAULT 'PRIVATE' CHECK (world_access IN ('PRIVATE', 'PUBLIC_VIEW', 'PUBLIC_BUILD')),
    time_mode VARCHAR(6) NOT NULL DEFAULT 'CYCLE' CHECK (time_mode IN ('CYCLE', 'DAY', 'NOON', 'SUNSET', 'NIGHT')),
    weather_mode VARCHAR(7) NOT NULL DEFAULT 'CYCLE' CHECK (weather_mode IN ('CYCLE', 'CLEAR', 'RAIN', 'THUNDER')),
    spawn_world VARCHAR(128),
    spawn_x DOUBLE PRECISION,
    spawn_y DOUBLE PRECISION,
    spawn_z DOUBLE PRECISION,
    spawn_yaw REAL,
    spawn_pitch REAL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_guilds_name_lower ON {{table:guilds}} (LOWER(name));

CREATE TABLE IF NOT EXISTS {{table:members}} (
    id BIGSERIAL PRIMARY KEY,
    guild_uuid VARCHAR(46) NOT NULL,
    player_uuid VARCHAR(46) NOT NULL,
    role VARCHAR(20) NOT NULL,
    joined_at BIGINT NOT NULL,
    CONSTRAINT uq_members_player UNIQUE (player_uuid)
);

CREATE TABLE IF NOT EXISTS {{table:warps}} (
    id BIGSERIAL PRIMARY KEY,
    guild_uuid VARCHAR(46) NOT NULL,
    name VARCHAR(16) NOT NULL,
    world VARCHAR(128) NOT NULL,
    x DOUBLE PRECISION NOT NULL,
    y DOUBLE PRECISION NOT NULL,
    z DOUBLE PRECISION NOT NULL,
    yaw REAL NOT NULL,
    pitch REAL NOT NULL,
    CONSTRAINT uq_warps_guild_name UNIQUE (guild_uuid, name)
);

CREATE TABLE IF NOT EXISTS {{table:invites}} (
    id BIGSERIAL PRIMARY KEY,
    guild_uuid VARCHAR(46) NOT NULL,
    inviter_uuid VARCHAR(46) NOT NULL,
    invitee_uuid VARCHAR(46) NOT NULL,
    expires_at BIGINT NOT NULL,
    CONSTRAINT uq_invites_guild_invitee UNIQUE (guild_uuid, invitee_uuid)
);

CREATE TABLE IF NOT EXISTS {{table:invite_history}} (
    guild_uuid VARCHAR(46) NOT NULL,
    created_at BIGINT NOT NULL,
    FOREIGN KEY (guild_uuid) REFERENCES {{table:guilds}} (guild_uuid) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_invite_history_guild_created ON {{table:invite_history}} (guild_uuid, created_at);

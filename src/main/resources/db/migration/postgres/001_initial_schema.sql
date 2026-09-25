CREATE TABLE IF NOT EXISTS {{table:guilds}} (
    guild_uuid VARCHAR(46) NOT NULL PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    prefix TEXT,
    owner_uuid VARCHAR(46) NOT NULL,
    created_at BIGINT NOT NULL,
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

CREATE TABLE IF NOT EXISTS {{table:guests}} (
    guild_uuid VARCHAR(46) NOT NULL,
    player_uuid VARCHAR(46) NOT NULL,
    editing BOOLEAN NOT NULL,
    expires_at BIGINT NOT NULL,
    PRIMARY KEY (guild_uuid, player_uuid),
    FOREIGN KEY (guild_uuid) REFERENCES {{table:guilds}} (guild_uuid) ON DELETE CASCADE
);

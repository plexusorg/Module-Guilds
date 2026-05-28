CREATE TABLE IF NOT EXISTS {{table:guilds}} (
    guild_uuid VARCHAR(46) NOT NULL PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE,
    prefix VARCHAR(64),
    owner_uuid VARCHAR(46) NOT NULL,
    created_at BIGINT NOT NULL,
    home_world VARCHAR(128),
    home_x DOUBLE PRECISION,
    home_y DOUBLE PRECISION,
    home_z DOUBLE PRECISION,
    home_yaw REAL,
    home_pitch REAL,
    motd VARCHAR(3000),
    tag_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_public BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS {{table:members}} (
    id BIGSERIAL PRIMARY KEY,
    guild_uuid VARCHAR(46) NOT NULL,
    player_uuid VARCHAR(46) NOT NULL,
    role VARCHAR(20) NOT NULL,
    joined_at BIGINT NOT NULL,
    CONSTRAINT uq_members_guild_player UNIQUE (guild_uuid, player_uuid)
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

CREATE TABLE IF NOT EXISTS {{table:guilds}} (
    guild_uuid VARCHAR(46) NOT NULL PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE COLLATE NOCASE,
    prefix TEXT,
    prefix_key VARCHAR(5) UNIQUE COLLATE NOCASE,
    owner_uuid VARCHAR(46) NOT NULL,
    created_at BIGINT NOT NULL,
    world_access VARCHAR(12) NOT NULL DEFAULT 'PRIVATE' CHECK (world_access IN ('PRIVATE', 'PUBLIC_VIEW', 'PUBLIC_BUILD')),
    time_mode VARCHAR(6) NOT NULL DEFAULT 'CYCLE' CHECK (time_mode IN ('CYCLE', 'DAY', 'NOON', 'SUNSET', 'NIGHT')),
    weather_mode VARCHAR(7) NOT NULL DEFAULT 'CYCLE' CHECK (weather_mode IN ('CYCLE', 'CLEAR', 'RAIN', 'THUNDER')),
    spawn_world VARCHAR(128),
    spawn_x DOUBLE,
    spawn_y DOUBLE,
    spawn_z DOUBLE,
    spawn_yaw FLOAT,
    spawn_pitch FLOAT
);

CREATE TABLE IF NOT EXISTS {{table:members}} (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_uuid VARCHAR(46) NOT NULL,
    player_uuid VARCHAR(46) NOT NULL,
    role VARCHAR(20) NOT NULL,
    joined_at BIGINT NOT NULL,
    UNIQUE (player_uuid)
);

CREATE TABLE IF NOT EXISTS {{table:warps}} (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_uuid VARCHAR(46) NOT NULL,
    name VARCHAR(16) NOT NULL,
    world VARCHAR(128) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    UNIQUE (guild_uuid, name)
);

CREATE TABLE IF NOT EXISTS {{table:invites}} (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_uuid VARCHAR(46) NOT NULL,
    inviter_uuid VARCHAR(46) NOT NULL,
    invitee_uuid VARCHAR(46) NOT NULL,
    expires_at BIGINT NOT NULL,
    UNIQUE (guild_uuid, invitee_uuid)
);

CREATE TABLE IF NOT EXISTS {{table:invite_history}} (
    guild_uuid VARCHAR(46) NOT NULL,
    created_at BIGINT NOT NULL,
    FOREIGN KEY (guild_uuid) REFERENCES {{table:guilds}} (guild_uuid) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_invite_history_guild_created ON {{table:invite_history}} (guild_uuid, created_at);

CREATE TABLE IF NOT EXISTS {{table:guilds}} (
    guild_uuid VARCHAR(46) NOT NULL PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE COLLATE NOCASE,
    prefix TEXT,
    owner_uuid VARCHAR(46) NOT NULL,
    created_at BIGINT NOT NULL,
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

CREATE TABLE IF NOT EXISTS {{table:guests}} (
    guild_uuid VARCHAR(46) NOT NULL,
    player_uuid VARCHAR(46) NOT NULL,
    editing BOOLEAN NOT NULL,
    expires_at BIGINT NOT NULL,
    PRIMARY KEY (guild_uuid, player_uuid),
    FOREIGN KEY (guild_uuid) REFERENCES {{table:guilds}} (guild_uuid) ON DELETE CASCADE
);

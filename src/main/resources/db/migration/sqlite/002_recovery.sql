CREATE TABLE IF NOT EXISTS {{table:sessions}} (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid VARCHAR(46) NOT NULL,
    player_name VARCHAR(16) NOT NULL,
    started_at BIGINT NOT NULL,
    ended_at BIGINT
);

CREATE INDEX IF NOT EXISTS idx_guilds_sessions_player_started ON {{table:sessions}} (player_uuid, started_at);

CREATE TABLE IF NOT EXISTS {{table:actions}} (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id BIGINT,
    actor_uuid VARCHAR(46),
    staff_override BOOLEAN NOT NULL,
    guild_uuid VARCHAR(46) NOT NULL,
    type VARCHAR(32) NOT NULL,
    created_at BIGINT NOT NULL,
    before_state TEXT,
    after_state TEXT,
    undone_at BIGINT
);

CREATE INDEX IF NOT EXISTS idx_guilds_actions_session ON {{table:actions}} (session_id);

CREATE INDEX IF NOT EXISTS idx_guilds_actions_guild_type ON {{table:actions}} (guild_uuid, type);

CREATE INDEX IF NOT EXISTS idx_guilds_actions_created ON {{table:actions}} (created_at);

ALTER TABLE {{table:guilds}} ADD COLUMN deleted_at BIGINT NULL;

ALTER TABLE {{table:guilds}} ADD COLUMN deleted_by VARCHAR(46) NULL;

CREATE INDEX IF NOT EXISTS idx_guilds_deleted_at ON {{table:guilds}} (deleted_at);

CREATE TABLE IF NOT EXISTS {{table:history_purges}} (
    world VARCHAR(38) NOT NULL,
    until_at BIGINT NOT NULL,
    PRIMARY KEY (world, until_at)
);

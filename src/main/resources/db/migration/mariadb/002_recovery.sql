CREATE TABLE IF NOT EXISTS {{table:sessions}} (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `player_uuid` VARCHAR(46) NOT NULL,
    `player_name` VARCHAR(16) NOT NULL,
    `started_at` BIGINT NOT NULL,
    `ended_at` BIGINT,
    PRIMARY KEY (`id`),
    KEY idx_guilds_sessions_player_started (player_uuid, started_at)
);

CREATE TABLE IF NOT EXISTS {{table:actions}} (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `session_id` BIGINT,
    `actor_uuid` VARCHAR(46),
    `staff_override` BOOLEAN NOT NULL,
    `guild_uuid` VARCHAR(46) NOT NULL,
    `type` VARCHAR(32) NOT NULL,
    `created_at` BIGINT NOT NULL,
    `before_state` TEXT,
    `after_state` TEXT,
    `undone_at` BIGINT,
    PRIMARY KEY (`id`),
    KEY idx_guilds_actions_session (session_id),
    KEY idx_guilds_actions_guild_type (guild_uuid, type),
    KEY idx_guilds_actions_created (created_at)
);

ALTER TABLE {{table:guilds}}
    ADD COLUMN `deleted_at` BIGINT NULL,
    ADD COLUMN `deleted_by` VARCHAR(46) NULL,
    ADD KEY idx_guilds_deleted_at (deleted_at);

CREATE TABLE IF NOT EXISTS {{table:history_purges}} (
    `world` VARCHAR(38) NOT NULL,
    `until_at` BIGINT NOT NULL,
    PRIMARY KEY (`world`, `until_at`)
);

CREATE TABLE IF NOT EXISTS {{table:guilds}} (
    `guild_uuid` VARCHAR(46) NOT NULL,
    `name` VARCHAR(64) NOT NULL,
    `prefix` VARCHAR(64),
    `owner_uuid` VARCHAR(46) NOT NULL,
    `created_at` BIGINT NOT NULL,
    `home_world` VARCHAR(128),
    `home_x` DOUBLE,
    `home_y` DOUBLE,
    `home_z` DOUBLE,
    `home_yaw` FLOAT,
    `home_pitch` FLOAT,
    `motd` VARCHAR(3000),
    `tag_enabled` BOOLEAN NOT NULL DEFAULT TRUE,
    `is_public` BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (`guild_uuid`),
    UNIQUE KEY `uq_guilds_name` (`name`)
);

CREATE TABLE IF NOT EXISTS {{table:members}} (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `guild_uuid` VARCHAR(46) NOT NULL,
    `player_uuid` VARCHAR(46) NOT NULL,
    `role` VARCHAR(20) NOT NULL,
    `joined_at` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_members_guild_player` (`guild_uuid`, `player_uuid`)
);

CREATE TABLE IF NOT EXISTS {{table:warps}} (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `guild_uuid` VARCHAR(46) NOT NULL,
    `name` VARCHAR(16) NOT NULL,
    `world` VARCHAR(128) NOT NULL,
    `x` DOUBLE NOT NULL,
    `y` DOUBLE NOT NULL,
    `z` DOUBLE NOT NULL,
    `yaw` FLOAT NOT NULL,
    `pitch` FLOAT NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_warps_guild_name` (`guild_uuid`, `name`)
);

CREATE TABLE IF NOT EXISTS {{table:invites}} (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `guild_uuid` VARCHAR(46) NOT NULL,
    `inviter_uuid` VARCHAR(46) NOT NULL,
    `invitee_uuid` VARCHAR(46) NOT NULL,
    `expires_at` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_invites_guild_invitee` (`guild_uuid`, `invitee_uuid`)
);

CREATE TABLE IF NOT EXISTS {{table:role_permissions}} (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `guild_uuid` VARCHAR(46) NOT NULL,
    `role` VARCHAR(20) NOT NULL,
    `block_breaking` BOOLEAN NOT NULL DEFAULT FALSE,
    `block_placing` BOOLEAN NOT NULL DEFAULT FALSE,
    `interacting` BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_role_permissions_guild_role` (`guild_uuid`, `role`)
);

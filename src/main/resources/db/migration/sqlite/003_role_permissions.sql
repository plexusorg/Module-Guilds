CREATE TABLE IF NOT EXISTS {{table:role_permissions}} (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_uuid VARCHAR(46) NOT NULL,
    role VARCHAR(20) NOT NULL,
    block_breaking BOOLEAN NOT NULL DEFAULT 0,
    block_placing BOOLEAN NOT NULL DEFAULT 0,
    interacting BOOLEAN NOT NULL DEFAULT 0,
    UNIQUE (guild_uuid, role)
);

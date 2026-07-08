ALTER TABLE {{table:guilds}} ADD COLUMN `member_block_breaking` BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE {{table:guilds}} ADD COLUMN `member_block_placing` BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE {{table:guilds}} ADD COLUMN `member_interacting` BOOLEAN NOT NULL DEFAULT FALSE;

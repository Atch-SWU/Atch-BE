-- 기존 아이템 ID·보유·지급 이력을 보존하고 새 등록 요청부터 재전송을 식별한다.
ALTER TABLE character_items
    ADD COLUMN item_code VARCHAR(80),
    ADD COLUMN asset_sha256 VARCHAR(64),
    ADD CONSTRAINT uq_character_items_item_code UNIQUE (item_code),
    ADD CONSTRAINT ck_character_items_registration_pair
        CHECK ((item_code IS NULL) = (asset_sha256 IS NULL)),
    ADD CONSTRAINT ck_character_items_item_code
        CHECK (item_code IS NULL OR item_code ~ '^[a-z0-9][a-z0-9-]{0,79}$'),
    ADD CONSTRAINT ck_character_items_asset_sha256
        CHECK (asset_sha256 IS NULL OR asset_sha256 ~ '^[a-f0-9]{64}$');

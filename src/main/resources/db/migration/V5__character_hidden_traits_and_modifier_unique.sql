DELETE m1
FROM character_owned_card_modifiers m1
JOIN character_owned_card_modifiers m2
    ON m1.owned_card_id = m2.owned_card_id
    AND m1.modifier_id = m2.modifier_id
    AND m1.id > m2.id;

CREATE UNIQUE INDEX uk_character_owned_card_modifiers_owned_modifier
    ON character_owned_card_modifiers (owned_card_id, modifier_id);

CREATE TABLE character_hidden_traits (
    id BIGINT NOT NULL AUTO_INCREMENT,
    character_id BIGINT NOT NULL,
    hidden_trait_id VARCHAR(80) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_character_hidden_traits_character_trait UNIQUE (character_id, hidden_trait_id),
    CONSTRAINT fk_character_hidden_traits_character
        FOREIGN KEY (character_id) REFERENCES character_profiles (id)
);

INSERT INTO character_hidden_traits (character_id, hidden_trait_id)
SELECT p.id, jt.hidden_trait_id
FROM character_profiles p
JOIN JSON_TABLE(
    CASE
        WHEN p.hidden_trait_ids IS NOT NULL AND JSON_VALID(p.hidden_trait_ids) THEN p.hidden_trait_ids
        ELSE '[]'
    END,
    '$[*]' COLUMNS (
        hidden_trait_id VARCHAR(80) PATH '$'
    )
) jt ON TRUE
WHERE jt.hidden_trait_id IS NOT NULL
  AND TRIM(jt.hidden_trait_id) <> ''
GROUP BY p.id, jt.hidden_trait_id;

ALTER TABLE character_profiles
    DROP COLUMN hidden_trait_ids;

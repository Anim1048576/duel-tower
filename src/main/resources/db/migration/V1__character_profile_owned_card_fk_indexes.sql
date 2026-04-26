CREATE INDEX ix_character_owned_cards_character_id
    ON character_owned_cards (character_id);

CREATE INDEX ix_character_owned_card_modifiers_owned_card_id
    ON character_owned_card_modifiers (owned_card_id);

ALTER TABLE character_owned_cards
    ADD CONSTRAINT fk_character_owned_cards_character
    FOREIGN KEY (character_id) REFERENCES character_profiles (id);

ALTER TABLE character_owned_card_modifiers
    ADD CONSTRAINT fk_character_owned_card_modifiers_owned_card
    FOREIGN KEY (owned_card_id) REFERENCES character_owned_cards (owned_card_id);

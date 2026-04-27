CREATE INDEX ix_character_current_skill_deck_entries_owned_card_id
    ON character_current_skill_deck_entries (owned_card_id);

ALTER TABLE character_current_skill_deck_entries
    ADD CONSTRAINT fk_character_current_skill_deck_entries_owned_card
    FOREIGN KEY (owned_card_id) REFERENCES character_owned_cards (owned_card_id);

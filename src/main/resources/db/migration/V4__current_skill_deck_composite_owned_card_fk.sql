CREATE UNIQUE INDEX uk_character_owned_cards_character_owned_card
    ON character_owned_cards (character_id, owned_card_id);

ALTER TABLE character_current_skill_deck_entries
    ADD CONSTRAINT fk_character_current_skill_deck_entries_character_owned_card
    FOREIGN KEY (character_id, owned_card_id)
    REFERENCES character_owned_cards (character_id, owned_card_id);

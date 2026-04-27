CREATE INDEX ix_character_current_skill_deck_entries_character_id
    ON character_current_skill_deck_entries (character_id);

ALTER TABLE character_ex_loadouts
    ADD CONSTRAINT fk_character_ex_loadouts_character
    FOREIGN KEY (character_id) REFERENCES character_profiles (id);

ALTER TABLE character_current_skill_deck_entries
    ADD CONSTRAINT fk_character_current_skill_deck_entries_character
    FOREIGN KEY (character_id) REFERENCES character_profiles (id);

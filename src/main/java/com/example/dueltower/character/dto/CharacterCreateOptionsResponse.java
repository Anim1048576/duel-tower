package com.example.dueltower.character.dto;

import java.util.List;

public record CharacterCreateOptionsResponse(
        List<Option> genderOptions,
        List<Option> orderAxisOptions,
        List<Option> moralAxisOptions,
        List<Option> hiddenTraitOptions
) {
    public record Option(String id, String label, String description) {}
}

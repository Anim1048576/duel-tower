package com.example.dueltower.content.card.model.playspec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = DiscardFromHandRequirement.class, name = "discard_from_hand"),
        @JsonSubTypes.Type(value = ChoiceRequirement.class, name = "choice"),
        @JsonSubTypes.Type(value = SelectFieldCardsRequirement.class, name = "select_field_cards")
})
public sealed interface ExtraPlayRequirement permits DiscardFromHandRequirement, ChoiceRequirement, SelectFieldCardsRequirement {
}

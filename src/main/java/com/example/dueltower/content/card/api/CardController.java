package com.example.dueltower.content.card.api;

import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.engine.model.CardDefinition;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/content/cards")
public class CardController {
    private final CardService service;

    public CardController(CardService service) {
        this.service = service;
    }

    @GetMapping
    public List<CardDefinition> list() {
        return service.list();
    }
}
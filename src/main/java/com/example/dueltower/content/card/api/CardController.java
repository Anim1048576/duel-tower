package com.example.dueltower.content.card.api;

import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public List<CardDefinition> list(@RequestParam(required = false) CardType type) {
        if (type == null) {
            return service.list();
        }
        return service.list(type);
    }
}

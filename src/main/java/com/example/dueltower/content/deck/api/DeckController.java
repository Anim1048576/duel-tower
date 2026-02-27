package com.example.dueltower.content.deck.api;

import com.example.dueltower.content.deck.service.DeckService;
import com.example.dueltower.content.deck.dto.AddDeckCardsRequest;
import com.example.dueltower.content.deck.dto.CreateDeckRequest;
import com.example.dueltower.content.deck.dto.DeckResponse;
import com.example.dueltower.content.deck.dto.UpdateDeckRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/content/decks")
public class DeckController {

    private final DeckService deckService;

    public DeckController(DeckService deckService) {
        this.deckService = deckService;
    }

    @PostMapping
    public DeckResponse create(@RequestBody(required = false) CreateDeckRequest req) {
        return deckService.create(req);
    }

    @GetMapping
    public List<DeckResponse> list() {
        return deckService.list();
    }

    @GetMapping("/{id}")
    public DeckResponse get(@PathVariable long id) {
        return deckService.get(id);
    }

    @PutMapping("/{id}")
    public DeckResponse update(@PathVariable long id, @RequestBody(required = false) UpdateDeckRequest req) {
        return deckService.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        deckService.delete(id);
    }

    @PostMapping("/{id}/cards/add")
    public DeckResponse addCards(
            @PathVariable long id,
            @RequestBody(required = false) AddDeckCardsRequest req
    ) {
        return deckService.addCards(id, req);
    }
}

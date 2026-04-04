package com.example.dueltower.content.item.api;

import com.example.dueltower.content.item.service.ItemService;
import com.example.dueltower.engine.model.ItemDefinition;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/content/items")
public class ItemController {
    private final ItemService service;

    public ItemController(ItemService service) {
        this.service = service;
    }

    @GetMapping
    public List<ItemDefinition> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ItemDefinition get(@PathVariable String id) {
        return service.get(id);
    }
}

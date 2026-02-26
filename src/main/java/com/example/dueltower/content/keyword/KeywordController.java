package com.example.dueltower.content.keyword;

import com.example.dueltower.engine.model.KeywordDefinition;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/content/keywords")
public class KeywordController {
    private final KeywordService service;

    public KeywordController(KeywordService service) {
        this.service = service;
    }

    @GetMapping
    public List<KeywordDefinition> list() {
        return service.list();
    }
}

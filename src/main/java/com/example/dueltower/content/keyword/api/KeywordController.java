package com.example.dueltower.content.keyword.api;

import com.example.dueltower.content.keyword.dto.KeywordResponse;
import com.example.dueltower.content.keyword.service.KeywordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    public List<KeywordResponse> list() {
        return service.listForApi();
    }

    @GetMapping("/all")
    public List<KeywordResponse> listAll() {
        return service.listAllForApi();
    }

    @GetMapping("/{id}/attached")
    public List<KeywordResponse> listAttached(@PathVariable String id) {
        return service.listAttachedToForApi(id);
    }

    @GetMapping("/{id}")
    public KeywordResponse get(@PathVariable String id) {
        return service.getForApi(id);
    }
}

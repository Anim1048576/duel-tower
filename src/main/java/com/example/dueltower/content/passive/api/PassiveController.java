package com.example.dueltower.content.passive.api;

import com.example.dueltower.content.passive.service.PassiveService;
import com.example.dueltower.engine.model.PassiveDefinition;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/content/passives")
public class PassiveController {
    private final PassiveService service;

    public PassiveController(PassiveService service) {
        this.service = service;
    }

    @GetMapping
    public List<PassiveDefinition> list() {
        return service.list();
    }
}

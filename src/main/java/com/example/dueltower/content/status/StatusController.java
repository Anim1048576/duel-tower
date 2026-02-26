package com.example.dueltower.content.status;

import com.example.dueltower.engine.model.StatusDefinition;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/content/statuses")
public class StatusController {
    private final StatusService service;

    public StatusController(StatusService service) {
        this.service = service;
    }

    @GetMapping
    public List<StatusDefinition> list() {
        return service.list();
    }
}

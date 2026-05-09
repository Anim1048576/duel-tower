package com.example.dueltower.content.status.api;

import com.example.dueltower.content.status.dto.StatusResponse;
import com.example.dueltower.content.status.service.StatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    public List<StatusResponse> list() {
        return service.listForApi();
    }

    @GetMapping("/all")
    public List<StatusResponse> listAll() {
        return service.listAllForApi();
    }

    @GetMapping("/{id}")
    public StatusResponse get(@PathVariable String id) {
        return service.getForApi(id);
    }
}

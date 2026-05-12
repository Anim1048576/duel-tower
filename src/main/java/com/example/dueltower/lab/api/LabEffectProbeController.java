package com.example.dueltower.lab.api;

import com.example.dueltower.lab.dto.LabEffectProbeRequest;
import com.example.dueltower.lab.dto.LabEffectProbeResponse;
import com.example.dueltower.lab.dto.LabProbeCardOptionDto;
import com.example.dueltower.lab.service.LabEffectProbeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/lab/effects")
public class LabEffectProbeController {

    private final LabEffectProbeService labEffectProbeService;

    public LabEffectProbeController(LabEffectProbeService labEffectProbeService) {
        this.labEffectProbeService = labEffectProbeService;
    }

    @GetMapping("/cards")
    public List<LabProbeCardOptionDto> cards() {
        return labEffectProbeService.cards();
    }

    @PostMapping("/probe")
    public LabEffectProbeResponse probe(@RequestBody(required = false) LabEffectProbeRequest request) {
        return labEffectProbeService.probe(request);
    }
}

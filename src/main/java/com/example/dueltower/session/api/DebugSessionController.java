package com.example.dueltower.session.api;

import com.example.dueltower.session.dto.DebugSoloCombatRequest;
import com.example.dueltower.session.dto.DebugSoloCombatResponse;
import com.example.dueltower.session.service.DebugSoloCombatService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/debug/sessions")
@ConditionalOnProperty(name = "dueltower.debug.enabled", havingValue = "true")
public class DebugSessionController {

    private final DebugSoloCombatService debugSoloCombatService;

    public DebugSessionController(DebugSoloCombatService debugSoloCombatService) {
        this.debugSoloCombatService = debugSoloCombatService;
    }

    @PostMapping("/solo-combat")
    public DebugSoloCombatResponse soloCombat(@RequestBody(required = false) DebugSoloCombatRequest req) {
        return debugSoloCombatService.startSoloCombat(req);
    }
}

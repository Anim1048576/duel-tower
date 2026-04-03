package com.example.dueltower.session.api;

import com.example.dueltower.session.dto.SessionEventPageResponse;
import com.example.dueltower.session.dto.SessionLogPageResponse;
import com.example.dueltower.session.service.SessionLogService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class SessionLogController {

    private final SessionLogService sessionLogService;

    public SessionLogController(SessionLogService sessionLogService) {
        this.sessionLogService = sessionLogService;
    }

    @GetMapping("/{code}/events")
    public SessionEventPageResponse events(@PathVariable String code,
                                           @RequestParam(required = false) Long afterVersion,
                                           @RequestParam(required = false) Integer limit,
                                           @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                                           @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                           Authentication authentication) {
        return sessionLogService.getEvents(code, afterVersion, limit, gmTokenHeader, playerTokenHeader, authentication);
    }

    @GetMapping("/{code}/logs")
    public SessionLogPageResponse logs(@PathVariable String code,
                                       @RequestParam(required = false) Long before,
                                       @RequestParam(required = false) Integer limit,
                                       @RequestHeader(value = "X-GM-Token", required = false) String gmTokenHeader,
                                       @RequestHeader(value = "X-Player-Token", required = false) String playerTokenHeader,
                                       Authentication authentication) {
        return sessionLogService.getLogs(code, before, limit, gmTokenHeader, playerTokenHeader, authentication);
    }
}

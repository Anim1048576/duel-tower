package com.example.dueltower.session;

import com.example.dueltower.engine.command.*;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.session.dto.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public CreateSessionResponse create(@RequestBody(required = false) CreateSessionRequest req) {
        String gmId = (req == null || req.gmId() == null || req.gmId().isBlank()) ? "gm" : req.gmId().trim();
        SessionRuntime rt = sessionService.createSession(gmId);
        return new CreateSessionResponse(rt.code(), rt.gmId(), StateMapper.toDto(rt.code(), rt.state()));
    }

    @GetMapping("/{code}")
    public SessionStateDto state(@PathVariable String code) {
        SessionRuntime rt = sessionService.get(code);
        return StateMapper.toDto(rt.code(), rt.state());
    }

    @PostMapping("/{code}/join")
    public JoinSessionResponse join(@PathVariable String code, @RequestBody JoinSessionRequest req) {
        if (req == null || req.playerId() == null || req.playerId().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "playerId is required");
        }
        SessionRuntime rt = sessionService.get(code);
        sessionService.join(code, req.playerId());
        return new JoinSessionResponse(StateMapper.toDto(rt.code(), rt.state()));
    }

    @PostMapping("/{code}/command")
    public EngineResponseDto command(@PathVariable String code, @RequestBody CommandRequest req) {
        if (req == null || req.type() == null || req.type().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "type is required");
        }
        SessionRuntime rt = sessionService.get(code);

        String t = req.type().trim().toUpperCase(Locale.ROOT);
        if ("START_COMBAT".equals(t)) {
            requirePlayer(req.playerId());
            if (!req.playerId().trim().equals(rt.gmId())) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "gm only");
            }
        }

        UUID commandId = parseOrNewUuid(req.commandId());
        long expectedVersion = (req.expectedVersion() == null) ? rt.state().version() : req.expectedVersion();

        GameCommand cmd = toCommand(req, commandId, expectedVersion);
        EngineResult res = rt.apply(cmd);

        return new EngineResponseDto(
                res.accepted(),
                res.errors(),
                StateMapper.toEventDtos(res.events()),
                StateMapper.toDto(rt.code(), res.state())
        );
    }

    private static UUID parseOrNewUuid(String v) {
        if (v == null || v.isBlank()) return UUID.randomUUID();
        try {
            return UUID.fromString(v.trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(BAD_REQUEST, "invalid commandId uuid");
        }
    }

    private static GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
        String type = req.type().trim().toUpperCase(Locale.ROOT);
        switch (type) {
            case "DRAW" -> {
                requirePlayer(req.playerId());
                int count = (req.count() == null) ? 1 : req.count();
                return new DrawCommand(commandId, expectedVersion, new PlayerId(req.playerId().trim()), count);
            }
            case "END_TURN" -> {
                requirePlayer(req.playerId());
                return new EndTurnCommand(commandId, expectedVersion, new PlayerId(req.playerId().trim()));
            }
            case "DISCARD_TO_HAND_LIMIT" -> {
                requirePlayer(req.playerId());
                List<String> raw = (req.discardIds() == null) ? List.of() : req.discardIds();
                List<CardInstId> ids = new ArrayList<>(raw.size());
                for (String s : raw) {
                    try {
                        ids.add(new Ids.CardInstId(UUID.fromString(s)));
                    } catch (Exception e) {
                        throw new ResponseStatusException(BAD_REQUEST, "invalid discardIds uuid: " + s);
                    }
                }
                return new DiscardToHandLimitCommand(commandId, expectedVersion, new PlayerId(req.playerId().trim()), ids);
            }
            default -> throw new ResponseStatusException(BAD_REQUEST, "unknown command type: " + req.type());
        }
    }

    private static void requirePlayer(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "playerId is required");
        }
    }
}
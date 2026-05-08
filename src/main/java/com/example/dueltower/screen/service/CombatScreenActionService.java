package com.example.dueltower.screen.service;

import com.example.dueltower.common.api.ApiErrorResolver;
import com.example.dueltower.common.api.ApiErrorResponse;
import com.example.dueltower.screen.dto.CombatScreenActionResponse;
import com.example.dueltower.screen.dto.CombatScreenResponse;
import com.example.dueltower.screen.dto.DisabledReasonDto;
import com.example.dueltower.screen.dto.ScreenActionDto;
import com.example.dueltower.session.dto.CommandRequest;
import com.example.dueltower.session.dto.EngineResponseDto;
import com.example.dueltower.session.service.SessionCommandService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class CombatScreenActionService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final CombatScreenService combatScreenService;
    private final SessionCommandService sessionCommandService;

    public CombatScreenActionService(CombatScreenService combatScreenService,
                                     SessionCommandService sessionCommandService) {
        this.combatScreenService = combatScreenService;
        this.sessionCommandService = sessionCommandService;
    }

    public CombatScreenActionResponse execute(String code,
                                              String actionId,
                                              String gmTokenHeader,
                                              String playerTokenHeader,
                                              Authentication authentication,
                                              CommandRequest request) {
        CombatScreenResponse currentScreen = combatScreenService.getScreen(
                code,
                null,
                null,
                gmTokenHeader,
                playerTokenHeader,
                authentication
        );
        ScreenActionDto action = findAction(currentScreen, actionId);

        DisabledReasonDto sourceBlockedReason = sourceBlockedReason(action, request);
        if (!action.enabled() || sourceBlockedReason != null) {
            DisabledReasonDto disabledReason = action.enabled() ? sourceBlockedReason : action.disabledReason();
            return blocked(
                    action,
                    disabledReason,
                    currentScreen,
                    action.enabled()
                            ? disabledReason.userMessage()
                            : defaultBlockedMessage(action, disabledReason)
            );
        }

        CommandRequest command = mergeCommandTemplate(action.payloadTemplate(), request);
        EngineResponseDto response = sessionCommandService.handleCommand(code, gmTokenHeader, playerTokenHeader, command);
        CombatScreenResponse latestScreen = combatScreenService.getScreen(
                code,
                null,
                null,
                gmTokenHeader,
                playerTokenHeader,
                authentication
        );

        if (response.accepted()) {
            return success(action, command, response, latestScreen);
        }
        return failed(action, command, response, latestScreen);
    }

    private ScreenActionDto findAction(CombatScreenResponse screen,
                                       String actionId) {
        return screen.getPossibleActions().stream()
                .filter(action -> action.id().equals(actionId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "unknown combat action: " + actionId));
    }

    private CommandRequest mergeCommandTemplate(Map<String, Object> payloadTemplate,
                                                CommandRequest request) {
        CommandRequest template = payloadTemplate == null
                ? new CommandRequest(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null)
                : JSON.convertValue(payloadTemplate, CommandRequest.class);
        CommandRequest input = request == null
                ? new CommandRequest(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null)
                : request;

        return new CommandRequest(
                preferredText(input.type(), template.type()),
                preferredText(input.commandId(), template.commandId()),
                input.expectedVersion() != null ? input.expectedVersion() : template.expectedVersion(),
                preferredText(input.playerId(), template.playerId()),
                preferredText(input.enemyId(), template.enemyId()),
                input.count() != null ? input.count() : template.count(),
                input.discardIds() != null ? input.discardIds() : template.discardIds(),
                preferredText(input.cardId(), template.cardId()),
                preferredText(input.summonId(), template.summonId()),
                preferredText(input.itemId(), template.itemId()),
                preferredText(input.equipId(), template.equipId()),
                preferredText(input.inventoryEquipId(), template.inventoryEquipId()),
                preferredText(input.offerId(), template.offerId()),
                input.targetPlayerIds() != null ? input.targetPlayerIds() : template.targetPlayerIds(),
                input.targetEnemyIds() != null ? input.targetEnemyIds() : template.targetEnemyIds(),
                input.targets() != null ? input.targets() : template.targets(),
                input.tieGroupIndex() != null ? input.tieGroupIndex() : template.tieGroupIndex(),
                input.orderedActorKeys() != null ? input.orderedActorKeys() : template.orderedActorKeys(),
                input.selectedIds() != null ? input.selectedIds() : template.selectedIds(),
                preferredText(input.choiceId(), template.choiceId()),
                preferredText(input.resultId(), template.resultId()),
                input.resultIndex() != null ? input.resultIndex() : template.resultIndex(),
                preferredText(input.reason(), template.reason())
        );
    }

    private DisabledReasonDto sourceBlockedReason(ScreenActionDto action,
                                                  CommandRequest request) {
        if (!"combat.playCard".equals(action.id()) || request == null || request.trimmedCardId() == null) {
            return null;
        }

        Object sourceOptionsRaw = action.metadata() == null ? null : action.metadata().get("sourceOptions");
        if (!(sourceOptionsRaw instanceof List<?> sourceOptions)) {
            return null;
        }

        for (Object optionRaw : sourceOptions) {
            if (!(optionRaw instanceof Map<?, ?> option)) {
                continue;
            }
            Object instanceId = option.get("instanceId");
            if (!request.trimmedCardId().equals(instanceId)) {
                continue;
            }
            Object supported = option.get("supported");
            if (Boolean.TRUE.equals(supported)) {
                return null;
            }
            Object unsupportedReason = option.get("unsupportedReason");
            String reason = unsupportedReason == null ? null : unsupportedReason.toString();
            return new DisabledReasonDto(
                    "ACTION_REQUIREMENT_UNSUPPORTED",
                    "RULE",
                    reason == null || reason.isBlank()
                            ? "The selected combat action source is not supported in this combat step."
                            : reason,
                    reason,
                    Map.of("actionId", action.id(), "cardId", request.trimmedCardId()),
                    null,
                    null
            );
        }
        return null;
    }

    private CombatScreenActionResponse blocked(ScreenActionDto action,
                                               DisabledReasonDto disabledReason,
                                               CombatScreenResponse screen,
                                               String message) {
        return new CombatScreenActionResponse(
                false,
                "BLOCKED",
                message,
                disabledReason,
                screen.getVersion(),
                List.of(message),
                resultSummary(action, null, null),
                screen
        );
    }

    private CombatScreenActionResponse success(ScreenActionDto action,
                                               CommandRequest command,
                                               EngineResponseDto response,
                                               CombatScreenResponse latestScreen) {
        String message = successMessage(action);
        return new CombatScreenActionResponse(
                true,
                "SUCCEEDED",
                message,
                null,
                latestScreen.getVersion(),
                List.of(message),
                resultSummary(action, command, response),
                latestScreen
        );
    }

    private CombatScreenActionResponse failed(ScreenActionDto action,
                                              CommandRequest command,
                                              EngineResponseDto response,
                                              CombatScreenResponse latestScreen) {
        DisabledReasonDto disabledReason = toDisabledReason(response);
        String message = primaryMessage(response, action);
        return new CombatScreenActionResponse(
                false,
                "FAILED",
                message,
                disabledReason,
                latestScreen.getVersion(),
                List.of(message),
                resultSummary(action, command, response),
                latestScreen
        );
    }

    private Map<String, Object> resultSummary(ScreenActionDto action,
                                              CommandRequest command,
                                              EngineResponseDto response) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("actionId", action.id());
        summary.put("commandType", command == null ? null : command.normalizedType());
        summary.put("accepted", response != null && response.accepted());
        summary.put("eventCount", response == null || response.events() == null ? 0 : response.events().size());
        summary.put("eventTypes", response == null || response.events() == null
                ? List.of()
                : response.events().stream().map(event -> event.type()).toList());
        return summary;
    }

    private DisabledReasonDto toDisabledReason(EngineResponseDto response) {
        ApiErrorResponse apiError = (response.errorDetails() == null || response.errorDetails().isEmpty())
                ? ApiErrorResolver.commandRejection(response.errors())
                : response.errorDetails().get(0);
        return DisabledReasonDto.fromApiErrorResponse(apiError);
    }

    private String primaryMessage(EngineResponseDto response,
                                  ScreenActionDto action) {
        if (response.errorDetails() != null && !response.errorDetails().isEmpty()) {
            return response.errorDetails().get(0).userMessage();
        }
        if (response.errors() != null && !response.errors().isEmpty()) {
            return response.errors().get(0);
        }
        return action.label() + " could not be completed.";
    }

    private String successMessage(ScreenActionDto action) {
        return switch (action.id()) {
            case "combat.draw" -> "Draw completed.";
            case "combat.endTurn" -> "Turn ended.";
            case "combat.playCard" -> "Card command completed.";
            case "combat.useEx" -> "EX command completed.";
            case "combat.handSwap" -> "패 교환을 완료했습니다.";
            case "combat.resolvePending" -> "Pending decision resolved.";
            case "combat.clearRecentResults" -> "Recent results cleared.";
            default -> action.label() + " completed.";
        };
    }

    private String defaultBlockedMessage(ScreenActionDto action,
                                         DisabledReasonDto disabledReason) {
        if (disabledReason != null && disabledReason.userMessage() != null && !disabledReason.userMessage().isBlank()) {
            return disabledReason.userMessage();
        }
        return action.label() + " is currently disabled.";
    }

    private String preferredText(String value,
                                 String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}

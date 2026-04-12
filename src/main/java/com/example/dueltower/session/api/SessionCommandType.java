package com.example.dueltower.session.api;

import com.example.dueltower.engine.command.*;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.TargetRef;
import com.example.dueltower.engine.model.TargetSelection;
import com.example.dueltower.session.dto.CommandRequest;
import com.example.dueltower.session.dto.TargetRefDto;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

public enum SessionCommandType {
    START_COMBAT("START_COMBAT", SessionCommandAuth.GM, true) {
        @Override
        public GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
            PlayerId playerId = parsePlayerId(requireText(req.trimmedPlayerId(), "playerId"));
            return new StartCombatCommand(commandId, expectedVersion, playerId);
        }
    },
    DRAW("DRAW", SessionCommandAuth.PLAYER, false) {
        @Override
        public GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
            PlayerId playerId = commandPlayerId(req);
            return new DrawCommand(commandId, expectedVersion, playerId, req.countOrDefault(1));
        }
    },
    END_TURN("END_TURN", SessionCommandAuth.PLAYER, false) {
        @Override
        public GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
            return new EndTurnCommand(commandId, expectedVersion, commandPlayerId(req));
        }
    },
    HAND_SWAP("HAND_SWAP", SessionCommandAuth.PLAYER, false) {
        @Override
        public GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
            CardInstId id = parseSingleCardInstId(req.discardIds(), "discardIds");
            return new HandSwapCommand(commandId, expectedVersion, commandPlayerId(req), id);
        }
    },
    PLAY_CARD("PLAY_CARD", SessionCommandAuth.PLAYER, false) {
        @Override
        public GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
            CardInstId id = parseCardInstId(requireText(req.trimmedCardId(), "cardId"), "cardId");
            return new PlayCardCommand(
                    commandId,
                    expectedVersion,
                    commandPlayerId(req),
                    id,
                    parseTargetSelection(req),
                    parseCardInstIds(req.discardIds(), "discardIds"),
                    parseCardInstIds(req.selectedIds(), "selectedIds")
            );
        }
    },
    USE_EX("USE_EX", SessionCommandAuth.PLAYER, false) {
        @Override
        public GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
            return new UseExCommand(commandId, expectedVersion, commandPlayerId(req), parseTargetSelection(req));
        }
    },
    ENEMY_PLAY_CARD("ENEMY_PLAY_CARD", SessionCommandAuth.GM, false) {
        @Override
        public GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
            Ids.EnemyId enemyId = parseEnemyId(req.enemyId());
            CardInstId id = parseCardInstId(requireText(req.trimmedCardId(), "cardId"), "cardId");
            return new EnemyPlayCardCommand(commandId, expectedVersion, enemyId, id, parseTargetSelection(req));
        }
    },
    ENEMY_USE_EX("ENEMY_USE_EX", SessionCommandAuth.GM, false) {
        @Override
        public GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
            return new EnemyUseExCommand(commandId, expectedVersion, parseEnemyId(req.enemyId()), parseTargetSelection(req));
        }
    },
    ENEMY_END_TURN("ENEMY_END_TURN", SessionCommandAuth.GM, false) {
        @Override
        public GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
            return new EnemyEndTurnCommand(commandId, expectedVersion, parseEnemyId(req.enemyId()));
        }
    },
    USE_SUMMON_ACTION("USE_SUMMON_ACTION", SessionCommandAuth.PLAYER, false) {
        @Override
        public GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
            Ids.SummonInstId summonId = parseSummonInstId(requireText(req.trimmedSummonId(), "summonId"), "summonId");
            return new UseSummonActionCommand(commandId, expectedVersion, commandPlayerId(req), summonId, parseTargetSelection(req));
        }
    },
    USE_EQUIP_ACTION("USE_EQUIP_ACTION", SessionCommandAuth.PLAYER, false) {
        @Override
        public GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
            String inventoryEquipId = requireText(req.trimmedInventoryEquipId(), "inventoryEquipId");
            return new UseEquipActionCommand(commandId, expectedVersion, commandPlayerId(req), inventoryEquipId, parseTargetSelection(req));
        }
    },
    RELOAD_EQUIPMENT("RELOAD_EQUIPMENT", SessionCommandAuth.PLAYER, false) {
        @Override
        public GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
            String inventoryEquipId = requireText(req.trimmedInventoryEquipId(), "inventoryEquipId");
            return new ReloadEquipmentCommand(commandId, expectedVersion, commandPlayerId(req), inventoryEquipId);
        }
    },
    USE_ITEM("USE_ITEM", SessionCommandAuth.PLAYER, false) {
        @Override
        public GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
            String itemId = requireText(req.trimmedItemId(), "itemId");
            return new UseItemCommand(commandId, expectedVersion, commandPlayerId(req), itemId, req.countOrDefault(1), parseTargetSelection(req));
        }
    },
    BUY_SHOP_ITEM("BUY_SHOP_ITEM", SessionCommandAuth.PLAYER, false) {
        @Override
        public GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
            String offerId = requireText(req.trimmedOfferId(), "offerId");
            return new BuyShopItemCommand(commandId, expectedVersion, commandPlayerId(req), offerId, req.countOrDefault(1));
        }
    },
    EQUIP_EQUIPMENT("EQUIP_EQUIPMENT", SessionCommandAuth.PLAYER, false) {
        @Override
        public GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
            String inventoryEquipId = requireText(req.trimmedInventoryEquipId(), "inventoryEquipId");
            return new EquipEquipmentCommand(commandId, expectedVersion, commandPlayerId(req), inventoryEquipId);
        }
    },
    UNEQUIP_EQUIPMENT("UNEQUIP_EQUIPMENT", SessionCommandAuth.PLAYER, false) {
        @Override
        public GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
            String inventoryEquipId = requireText(req.trimmedInventoryEquipId(), "inventoryEquipId");
            return new UnequipEquipmentCommand(commandId, expectedVersion, commandPlayerId(req), inventoryEquipId);
        }
    },
    OPEN_CHEST("OPEN_CHEST", SessionCommandAuth.PLAYER, false) {
        @Override
        public GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
            return new OpenChestCommand(commandId, expectedVersion, commandPlayerId(req), req.countOrDefault(1));
        }
    },
    RESOLVE_JUDGEMENT("RESOLVE_JUDGEMENT", SessionCommandAuth.PLAYER, false) {
        @Override
        public GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
            String choiceId = requireText(req.trimmedChoiceId(), "choiceId");
            return new ResolveJudgementCommand(commandId, expectedVersion, commandPlayerId(req), choiceId);
        }
    },
    SURRENDER_COMBAT("SURRENDER_COMBAT", SessionCommandAuth.PLAYER, false) {
        @Override
        public GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
            return new SurrenderCombatCommand(commandId, expectedVersion, commandPlayerId(req), req.trimmedReason());
        }
    },
    SELL_INVENTORY_ITEM("SELL_INVENTORY_ITEM", SessionCommandAuth.PLAYER, false) {
        @Override
        public GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
            return new SellInventoryItemCommand(
                    commandId,
                    expectedVersion,
                    commandPlayerId(req),
                    req.trimmedItemId(),
                    req.trimmedInventoryEquipId(),
                    req.countOrDefault(1)
            );
        }
    },
    RETREAT_COMBAT("RETREAT_COMBAT", SessionCommandAuth.PLAYER, false) {
        @Override
        public GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
            return new RetreatCombatCommand(commandId, expectedVersion, commandPlayerId(req), req.trimmedReason());
        }
    },
    DISCARD_TO_HAND_LIMIT("DISCARD_TO_HAND_LIMIT", SessionCommandAuth.PLAYER, false) {
        @Override
        public GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
            return new DiscardToHandLimitCommand(commandId, expectedVersion, commandPlayerId(req), parseCardInstIds(req.discardIds(), "discardIds"));
        }
    },
    RESOLVE_INITIATIVE_TIE("RESOLVE_INITIATIVE_TIE", SessionCommandAuth.PLAYER, false) {
        @Override
        public GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
            if (req.tieGroupIndex() == null) {
                throw new ResponseStatusException(BAD_REQUEST, "tieGroupIndex is required");
            }
            if (req.orderedActorKeys() == null || req.orderedActorKeys().isEmpty()) {
                throw new ResponseStatusException(BAD_REQUEST, "orderedActorKeys is required");
            }
            return new ResolveInitiativeTieCommand(
                    commandId,
                    expectedVersion,
                    commandPlayerId(req),
                    req.tieGroupIndex(),
                    req.orderedActorKeys()
            );
        }
    },
    SEARCH_PICK("SEARCH_PICK", SessionCommandAuth.PLAYER, false) {
        @Override
        public GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
            return new ResolveSearchPickCommand(commandId, expectedVersion, commandPlayerId(req), parseCardInstIds(req.selectedIds(), "selectedIds"));
        }
    },
    RESOLVE_SEARCH_PICK("RESOLVE_SEARCH_PICK", SessionCommandAuth.PLAYER, false) {
        @Override
        public GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
            return SEARCH_PICK.toCommand(req, commandId, expectedVersion);
        }
    },
    SELECT_NODE_CHOICE("SELECT_NODE_CHOICE", SessionCommandAuth.PLAYER, false) {
        @Override
        public GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
            String choiceId = requireText(req.trimmedChoiceId(), "choiceId");
            return new SelectNodeChoiceCommand(commandId, expectedVersion, commandPlayerId(req), choiceId);
        }
    },
    CLEAR_RECENT_RESULTS("CLEAR_RECENT_RESULTS", SessionCommandAuth.PLAYER, false) {
        @Override
        public GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion) {
            return new ClearRecentResultsCommand(commandId, expectedVersion, commandPlayerId(req));
        }
    };

    private final String requestType;
    private final SessionCommandAuth auth;
    private final boolean requiresPlayerId;

    SessionCommandType(String requestType, SessionCommandAuth auth, boolean requiresPlayerId) {
        this.requestType = requestType;
        this.auth = auth;
        this.requiresPlayerId = requiresPlayerId;
    }

    public abstract GameCommand toCommand(CommandRequest req, UUID commandId, long expectedVersion);

    public String requestType() {
        return requestType;
    }

    public boolean requiresPlayerAuthorization() {
        return auth == SessionCommandAuth.PLAYER;
    }

    public boolean requiresGmAuthorization() {
        return auth == SessionCommandAuth.GM;
    }

    public boolean requiresPlayerId() {
        return requiresPlayerId;
    }

    public static SessionCommandType from(String rawType) {
        String normalizedType = (rawType == null) ? "" : rawType.trim().toUpperCase(Locale.ROOT);
        for (SessionCommandType type : values()) {
            if (type.requestType.equals(normalizedType)) {
                return type;
            }
        }
        throw new ResponseStatusException(BAD_REQUEST, "unknown command type: " + rawType);
    }

    private static PlayerId commandPlayerId(CommandRequest req) {
        return parsePlayerId(requireText(req.trimmedPlayerId(), "playerId"));
    }

    private static PlayerId parsePlayerId(String playerId) {
        return new PlayerId(playerId.trim());
    }

    private static Ids.EnemyId parseEnemyId(String enemyId) {
        if (enemyId == null || enemyId.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "enemyId is required");
        }
        return new Ids.EnemyId(enemyId.trim());
    }

    private static CardInstId parseCardInstId(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, fieldName + " is blank");
        }
        try {
            return new CardInstId(UUID.fromString(raw.trim()));
        } catch (Exception e) {
            throw new ResponseStatusException(BAD_REQUEST, "invalid " + fieldName + " uuid: " + raw);
        }
    }

    private static Ids.SummonInstId parseSummonInstId(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, fieldName + " is blank");
        }
        try {
            return new Ids.SummonInstId(UUID.fromString(raw.trim()));
        } catch (Exception e) {
            throw new ResponseStatusException(BAD_REQUEST, "invalid " + fieldName + " uuid: " + raw);
        }
    }

    private static CardInstId parseSingleCardInstId(List<String> raw, String fieldName) {
        List<String> list = (raw == null) ? List.of() : raw;
        if (list.size() != 1) {
            throw new ResponseStatusException(BAD_REQUEST, fieldName + " must have exactly 1 id");
        }
        return parseCardInstId(list.get(0), fieldName + "[0]");
    }

    private static List<CardInstId> parseCardInstIds(List<String> raw, String fieldName) {
        List<String> list = (raw == null) ? List.of() : raw;
        List<CardInstId> ids = new ArrayList<>(list.size());
        for (String s : list) {
            ids.add(parseCardInstId(s, fieldName));
        }
        return ids;
    }

    private static TargetSelection parseTargetSelection(CommandRequest req) {
        List<TargetRef> targets = new ArrayList<>();

        if (req.targets() != null) {
            for (TargetRefDto dto : req.targets()) {
                if (dto == null) continue;
                if (dto.playerId() != null && !dto.playerId().isBlank()) {
                    targets.add(TargetRef.ofPlayer(new PlayerId(dto.playerId().trim())));
                    continue;
                }
                if (dto.enemyId() != null && !dto.enemyId().isBlank()) {
                    targets.add(TargetRef.ofEnemy(new Ids.EnemyId(dto.enemyId().trim())));
                    continue;
                }
                if (dto.summonOwnerPlayerId() != null && !dto.summonOwnerPlayerId().isBlank()
                        && dto.summonInstanceId() != null && !dto.summonInstanceId().isBlank()) {
                    targets.add(TargetRef.ofSummon(
                            new PlayerId(dto.summonOwnerPlayerId().trim()),
                            parseSummonInstId(dto.summonInstanceId(), "targets.summonInstanceId")
                    ));
                }
            }
        }

        if (req.targetPlayerIds() != null) {
            for (String s : req.targetPlayerIds()) {
                if (s == null || s.isBlank()) continue;
                targets.add(TargetRef.ofPlayer(new PlayerId(s.trim())));
            }
        }
        if (req.targetEnemyIds() != null) {
            for (String s : req.targetEnemyIds()) {
                if (s == null || s.isBlank()) continue;
                targets.add(TargetRef.ofEnemy(new Ids.EnemyId(s.trim())));
            }
        }
        return targets.isEmpty() ? TargetSelection.empty() : new TargetSelection(List.copyOf(targets));
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, fieldName + " is required");
        }
        return value.trim();
    }
}

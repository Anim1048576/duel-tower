package com.example.dueltower.screen.dto;

import com.example.dueltower.screen.support.ScreenApiContractTestSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenResponseSerializationContractTest extends ScreenApiContractTestSupport {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void sampleSerializedScreenJsonMatchesCommonContract() throws Exception {
        JsonNode body = JSON.readTree("""
                {
                  "screenKey": "DeckEditor",
                  "generatedAt": "2026-04-15T10:20:30+09:00",
                  "uiNotices": ["stub response"],
                  "possibleActions": [
                    {
                      "id": "deckEditor.create",
                      "label": "Create deck",
                      "method": "POST",
                      "href": "/api/content/decks",
                      "auth": "loginCookie",
                      "enabled": false,
                      "disabledReason": {
                        "code": "SCREEN_STUB_ACTION",
                        "category": "RULE",
                        "userMessage": "This action stays disabled until the screen implementation lands.",
                        "debugMessage": "Screen API contract test sample",
                        "details": {"stub": true},
                        "status": null,
                        "path": null
                      },
                      "metadata": {
                        "kind": "mutation"
                      },
                      "payloadTemplate": {
                        "name": "<deck-name>",
                        "type": "PLAYER",
                        "cards": []
                      }
                    }
                  ]
                }
                """);

        assertBaseScreenContract(body, "DeckEditor", true);

        JsonNode action = findAction(body, "deckEditor.create");
        assertDisabledActionContract(action);
        assertThat(action.path("auth").asText()).isEqualTo("loginCookie");
        assertThat(action.path("metadata").path("kind").asText()).isEqualTo("mutation");
        assertThat(body.path("possibleActions").size()).isEqualTo(1);
    }

    @Test
    void sampleCombatScreenJsonMatchesCombatContract() throws Exception {
        JsonNode body = JSON.readTree("""
                {
                  "screenKey": "Combat",
                  "generatedAt": "2026-04-18T19:10:30+09:00",
                  "uiNotices": ["combat screen sample"],
                  "sessionCode": "ABCD1234",
                  "version": 27,
                  "changed": true,
                  "status": {
                    "round": 2,
                    "phase": "PLAYER",
                    "currentActor": {
                      "raw": "P:p1",
                      "kind": "player",
                      "id": "p1",
                      "label": "p1",
                      "note": "p1 is the current acting player.",
                      "tone": "success"
                    },
                    "turnOrderSummary": "p1 -> e1",
                    "battlefieldSummary": "1 players | 1 enemies",
                    "runSummary": "Current node: test encounter",
                    "tieGroupSummary": null
                  },
                  "access": {
                    "role": "player",
                    "runtimePlayerId": "p1",
                    "expectedVersion": 27,
                    "guards": {
                      "canIssuePlayerCommand": true,
                      "canResolvePendingCommand": false,
                      "canClearRecentResultsCommand": true,
                      "canIssueGmCommand": false,
                      "exAvailable": true,
                      "hasPendingDecision": false,
                      "isCurrentTurnPlayer": true,
                      "hasCombatState": true
                    }
                  },
                  "actors": {
                    "players": [
                      {
                        "playerId": "p1",
                        "ready": true,
                        "stateLabel": "Ready",
                        "stateTone": "success",
                        "metrics": [{"label": "Hand", "value": 3, "note": "Limit 5"}],
                        "summaryLines": ["EX ready"],
                        "statusTags": [{"label": "EX ready", "tone": "warning"}],
                        "passives": ["P001"],
                        "handCards": [
                          {
                            "instanceId": "card-1",
                            "defId": "C001",
                            "title": "Strike",
                            "subtitle": "Attack",
                            "unresolved": false,
                            "tags": [{"label": "Attack", "tone": "accent"}],
                            "meta": "Instance card-1"
                          }
                        ],
                        "fieldCards": [],
                        "graveCards": [],
                        "excludedCards": [],
                        "exCard": {
                          "instanceId": "ex-1",
                          "defId": "EX901",
                          "title": "Meteor",
                          "subtitle": "EX",
                          "unresolved": false,
                          "tags": [{"label": "EX", "tone": "warning"}],
                          "meta": "Instance ex-1"
                        }
                      }
                    ],
                    "enemies": [],
                    "summons": []
                  },
                  "zones": {
                    "visiblePlayerId": "p1",
                    "hand": [
                      {
                        "instanceId": "card-1",
                        "defId": "C001",
                        "title": "Strike",
                        "subtitle": "Attack",
                        "unresolved": false,
                        "tags": [{"label": "Attack", "tone": "accent"}],
                        "meta": "Instance card-1"
                      }
                    ],
                    "field": [],
                    "grave": [],
                    "excluded": [],
                    "ex": {
                      "instanceId": "ex-1",
                      "defId": "EX901",
                      "title": "Meteor",
                      "subtitle": "EX",
                      "unresolved": false,
                      "tags": [{"label": "EX", "tone": "warning"}],
                      "meta": "Instance ex-1"
                    }
                  },
                  "sidebar": {
                    "events": [{"title": "CARD_PLAYED", "lines": ["p1 used Strike"]}],
                    "logs": [{"title": "INFO", "lines": ["version 27"]}],
                    "recentResults": [{"title": "Encounter clear", "summary": "Victory", "meta": "Combat | just now"}]
                  },
                  "possibleActions": [
                    {
                      "id": "combat.playCard",
                      "label": "Play selected card",
                      "method": "POST",
                      "href": "/api/screens/sessions/ABCD1234/combat/actions/combat.playCard",
                      "auth": "playerToken",
                      "enabled": true,
                      "disabledReason": null,
                      "payloadTemplate": {
                        "type": "PLAY_CARD",
                        "expectedVersion": 27,
                        "playerId": "p1",
                        "cardId": "",
                        "discardIds": [],
                        "selectedIds": [],
                        "targets": []
                      },
                      "metadata": {
                        "kind": "playCard",
                        "note": "Server-calculated command support and requirement views for each playable hand card.",
                        "localSelection": {
                          "requiresSelectedCard": true,
                          "sourceType": "handCard"
                        },
                        "sourceOptions": [
                          {
                            "instanceId": "card-1",
                            "title": "Strike",
                            "sourceCard": {
                              "instanceId": "card-1",
                              "defId": "C001",
                              "title": "Strike",
                              "subtitle": "Attack",
                              "unresolved": false,
                              "tags": [{"label": "Attack", "tone": "accent"}],
                              "meta": "Instance card-1"
                            },
                            "requirementView": {
                              "sourceLabel": "Strike",
                              "targetSummary": "Select exactly one enemy or summon target",
                              "discardSummary": "No extra hand discard required",
                              "selectedIdsSummary": "No extra field selection required",
                              "choiceSummary": "No explicit choice requirement",
                              "boardObjectSummary": "No board-object selection requirement",
                              "targetRule": {
                                "target": "ENEMY_ONE",
                                "requiredSelection": true
                              },
                              "discardRequirement": null,
                              "selectedIdsRequirement": null,
                              "boardObjectRequirement": null,
                              "boardObjectSelectionHints": null,
                              "pendingChoiceSchema": null,
                              "unsupportedReason": null
                            },
                            "supported": true,
                            "unsupportedReason": null
                          }
                        ]
                      }
                    }
                  ]
                }
                """);

        assertCombatScreenContract(body);
        assertCombatCardContract(body.path("zones").path("hand").get(0));
        JsonNode action = findAction(body, "combat.playCard");
        assertActionContract(action);
        assertThat(action.path("metadata").path("kind").asText()).isEqualTo("playCard");
        assertThat(action.path("metadata").path("sourceOptions").isArray()).isTrue();
        assertThat(action.path("metadata").path("sourceOptions").get(0).path("requirementView").path("targetRule").path("requiredSelection").asBoolean()).isTrue();
        assertThat(action.path("metadata").path("sourceOptions").get(0).path("supported").asBoolean()).isTrue();
    }

    @Test
    void sampleCombatActionResponseJsonMatchesCombatActionContract() throws Exception {
        JsonNode body = JSON.readTree("""
                {
                  "success": true,
                  "outcome": "SUCCEEDED",
                  "message": "Draw completed.",
                  "disabledReason": null,
                  "latestVersion": 28,
                  "serverNotices": ["Draw completed."],
                  "resultSummary": {
                    "actionId": "combat.draw",
                    "commandType": "DRAW",
                    "accepted": true,
                    "eventCount": 1,
                    "eventTypes": ["CARD_DRAWN"]
                  },
                  "latestScreen": {
                    "screenKey": "Combat",
                    "generatedAt": "2026-04-18T19:10:31+09:00",
                    "uiNotices": ["combat screen sample"],
                    "sessionCode": "ABCD1234",
                    "version": 28,
                    "changed": true,
                    "status": {
                      "round": 2,
                      "phase": "PLAYER",
                      "currentActor": {
                        "raw": "P:p1",
                        "kind": "player",
                        "id": "p1",
                        "label": "p1",
                        "note": "p1 is the current acting player.",
                        "tone": "success"
                      },
                      "turnOrderSummary": "p1 -> e1",
                      "battlefieldSummary": "1 players | 1 enemies",
                      "runSummary": "Current node: test encounter",
                      "tieGroupSummary": null
                    },
                    "access": {
                      "role": "player",
                      "runtimePlayerId": "p1",
                      "expectedVersion": 28,
                      "guards": {
                        "canIssuePlayerCommand": true,
                        "canResolvePendingCommand": false,
                        "canClearRecentResultsCommand": true,
                        "canIssueGmCommand": false,
                        "exAvailable": true,
                        "hasPendingDecision": false,
                        "isCurrentTurnPlayer": true,
                        "hasCombatState": true
                      }
                    },
                    "actors": {
                      "players": [],
                      "enemies": [],
                      "summons": []
                    },
                    "zones": {
                      "visiblePlayerId": "p1",
                      "hand": [],
                      "field": [],
                      "grave": [],
                      "excluded": [],
                      "ex": null
                    },
                    "sidebar": {
                      "events": [],
                      "logs": [],
                      "recentResults": []
                    },
                    "possibleActions": []
                  }
                }
                """);

        assertCombatActionResponseContract(body);
        assertThat(body.path("resultSummary").path("commandType").asText()).isEqualTo("DRAW");
        assertThat(body.path("latestVersion").asLong()).isEqualTo(body.path("latestScreen").path("version").asLong());
    }
}

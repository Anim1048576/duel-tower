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
        assertThat(body.path("possibleActions").size()).isEqualTo(1);
    }
}

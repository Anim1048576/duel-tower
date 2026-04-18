package com.example.dueltower.screen.support;

import org.assertj.core.api.Assertions;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public abstract class ScreenApiContractTestSupport {

    private static final ObjectMapper JSON = new ObjectMapper();

    protected JsonNode readJson(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    protected JsonNode assertBaseScreenContract(MvcResult result, String expectedScreenKey) throws Exception {
        JsonNode body = readJson(result);
        assertBaseScreenContract(body, expectedScreenKey, true);
        return body;
    }

    protected void assertBaseScreenContract(JsonNode body,
                                            String expectedScreenKey,
                                            boolean generatedAtRequired) {
        Assertions.assertThat(body.path("screenKey").isTextual()).isTrue();
        Assertions.assertThat(body.path("screenKey").asText()).isEqualTo(expectedScreenKey);
        assertGeneratedAtPolicy(body, generatedAtRequired);
        Assertions.assertThat(body.has("uiNotices")).isTrue();
        Assertions.assertThat(body.path("uiNotices").isArray()).isTrue();
        Assertions.assertThat(body.has("possibleActions")).isTrue();
        Assertions.assertThat(body.path("possibleActions").isArray()).isTrue();
    }

    protected void assertGeneratedAtPolicy(JsonNode body, boolean required) {
        if (!required) {
            return;
        }
        Assertions.assertThat(body.has("generatedAt")).isTrue();
        Assertions.assertThat(body.path("generatedAt").isTextual()).isTrue();
        Assertions.assertThat(body.path("generatedAt").asText()).isNotBlank();
    }

    protected JsonNode findAction(JsonNode screenBody, String actionId) {
        for (JsonNode action : screenBody.path("possibleActions")) {
            if (actionId.equals(action.path("id").asText())) {
                return action;
            }
        }
        throw new AssertionError("missing actionId in possibleActions: " + actionId);
    }

    protected void assertActionContract(JsonNode action) {
        Assertions.assertThat(action.path("id").isTextual()).isTrue();
        Assertions.assertThat(action.path("label").isTextual()).isTrue();
        Assertions.assertThat(action.path("method").isTextual()).isTrue();
        Assertions.assertThat(action.path("href").isTextual()).isTrue();
        Assertions.assertThat(action.path("auth").isTextual()).isTrue();
        Assertions.assertThat(action.path("enabled").isBoolean()).isTrue();
        Assertions.assertThat(action.has("disabledReason")).isTrue();
        Assertions.assertThat(action.has("payloadTemplate")).isTrue();
        Assertions.assertThat(action.has("metadata")).isTrue();
        Assertions.assertThat(action.path("payloadTemplate").isObject() || action.path("payloadTemplate").isNull()).isTrue();
        Assertions.assertThat(action.path("metadata").isObject() || action.path("metadata").isNull()).isTrue();
    }

    protected void assertDisabledActionContract(JsonNode action) {
        assertActionContract(action);
        Assertions.assertThat(action.path("enabled").asBoolean()).isFalse();
        JsonNode disabledReason = action.path("disabledReason");
        Assertions.assertThat(disabledReason.isObject()).isTrue();
        assertDisabledReasonContract(disabledReason);
    }

    protected void assertDisabledReasonContract(JsonNode disabledReason) {
        Assertions.assertThat(disabledReason.has("code")).isTrue();
        Assertions.assertThat(disabledReason.path("code").isTextual()).isTrue();
        Assertions.assertThat(disabledReason.has("category")).isTrue();
        Assertions.assertThat(disabledReason.path("category").isTextual() || disabledReason.path("category").isNull()).isTrue();
        Assertions.assertThat(disabledReason.has("userMessage")).isTrue();
        Assertions.assertThat(disabledReason.path("userMessage").isTextual()).isTrue();
        Assertions.assertThat(disabledReason.has("debugMessage")).isTrue();
        Assertions.assertThat(disabledReason.path("debugMessage").isTextual() || disabledReason.path("debugMessage").isNull()).isTrue();
        Assertions.assertThat(disabledReason.has("details")).isTrue();
        Assertions.assertThat(disabledReason.has("status")).isTrue();
        Assertions.assertThat(disabledReason.path("status").isInt() || disabledReason.path("status").isNull()).isTrue();
        Assertions.assertThat(disabledReason.has("path")).isTrue();
        Assertions.assertThat(disabledReason.path("path").isTextual() || disabledReason.path("path").isNull()).isTrue();
    }

    protected void assertCombatScreenContract(JsonNode body) {
        assertBaseScreenContract(body, "Combat", true);
        Assertions.assertThat(body.path("sessionCode").isTextual()).isTrue();
        Assertions.assertThat(body.path("version").canConvertToLong()).isTrue();
        Assertions.assertThat(body.path("changed").isBoolean()).isTrue();
        Assertions.assertThat(body.path("status").isObject()).isTrue();
        Assertions.assertThat(body.path("access").isObject()).isTrue();
        Assertions.assertThat(body.path("actors").isObject()).isTrue();
        Assertions.assertThat(body.path("zones").isObject()).isTrue();
        Assertions.assertThat(body.path("sidebar").isObject()).isTrue();

        Assertions.assertThat(body.path("status").path("round").isInt() || body.path("status").path("round").isNull()).isTrue();
        Assertions.assertThat(body.path("status").has("phase")).isTrue();
        Assertions.assertThat(body.path("status").path("phase").isTextual() || body.path("status").path("phase").isNull()).isTrue();
        Assertions.assertThat(body.path("status").has("currentActor")).isTrue();
        Assertions.assertThat(body.path("status").path("currentActor").isObject() || body.path("status").path("currentActor").isNull()).isTrue();
        Assertions.assertThat(body.path("status").path("turnOrderSummary").isTextual()).isTrue();
        Assertions.assertThat(body.path("status").path("battlefieldSummary").isTextual()).isTrue();
        Assertions.assertThat(body.path("status").path("runSummary").isTextual()).isTrue();
        Assertions.assertThat(body.path("status").has("tieGroupSummary")).isTrue();
        Assertions.assertThat(body.path("status").path("tieGroupSummary").isTextual() || body.path("status").path("tieGroupSummary").isNull()).isTrue();

        Assertions.assertThat(body.path("access").path("role").isTextual()).isTrue();
        Assertions.assertThat(body.path("access").has("runtimePlayerId")).isTrue();
        Assertions.assertThat(body.path("access").path("runtimePlayerId").isTextual() || body.path("access").path("runtimePlayerId").isNull()).isTrue();
        Assertions.assertThat(body.path("access").path("expectedVersion").canConvertToLong()).isTrue();
        Assertions.assertThat(body.path("access").path("guards").isObject()).isTrue();

        Assertions.assertThat(body.path("actors").path("players").isArray()).isTrue();
        Assertions.assertThat(body.path("actors").path("enemies").isArray()).isTrue();
        Assertions.assertThat(body.path("actors").path("summons").isArray()).isTrue();

        Assertions.assertThat(body.path("zones").has("visiblePlayerId")).isTrue();
        Assertions.assertThat(body.path("zones").path("visiblePlayerId").isTextual() || body.path("zones").path("visiblePlayerId").isNull()).isTrue();
        Assertions.assertThat(body.path("zones").path("hand").isArray()).isTrue();
        Assertions.assertThat(body.path("zones").path("field").isArray()).isTrue();
        Assertions.assertThat(body.path("zones").path("grave").isArray()).isTrue();
        Assertions.assertThat(body.path("zones").path("excluded").isArray()).isTrue();
        Assertions.assertThat(body.path("zones").has("ex")).isTrue();
        Assertions.assertThat(body.path("zones").path("ex").isObject() || body.path("zones").path("ex").isNull()).isTrue();

        Assertions.assertThat(body.path("sidebar").path("events").isArray()).isTrue();
        Assertions.assertThat(body.path("sidebar").path("logs").isArray()).isTrue();
        Assertions.assertThat(body.path("sidebar").path("recentResults").isArray()).isTrue();
    }

    protected void assertCombatCardContract(JsonNode card) {
        Assertions.assertThat(card.path("instanceId").isTextual()).isTrue();
        Assertions.assertThat(card.has("defId")).isTrue();
        Assertions.assertThat(card.path("defId").isTextual() || card.path("defId").isNull()).isTrue();
        Assertions.assertThat(card.path("title").isTextual()).isTrue();
        Assertions.assertThat(card.path("subtitle").isTextual()).isTrue();
        Assertions.assertThat(card.path("unresolved").isBoolean()).isTrue();
        Assertions.assertThat(card.path("tags").isArray()).isTrue();
        Assertions.assertThat(card.has("meta")).isTrue();
        Assertions.assertThat(card.path("meta").isTextual() || card.path("meta").isNull()).isTrue();
    }

    protected void assertCombatActionResponseContract(JsonNode body) {
        Assertions.assertThat(body.path("success").isBoolean()).isTrue();
        Assertions.assertThat(body.path("outcome").isTextual()).isTrue();
        Assertions.assertThat(body.path("message").isTextual()).isTrue();
        Assertions.assertThat(body.has("disabledReason")).isTrue();
        Assertions.assertThat(body.path("latestVersion").isNumber() || body.path("latestVersion").isNull()).isTrue();
        Assertions.assertThat(body.path("serverNotices").isArray()).isTrue();
        Assertions.assertThat(body.path("resultSummary").isObject() || body.path("resultSummary").isNull()).isTrue();
        Assertions.assertThat(body.has("latestScreen")).isTrue();
        Assertions.assertThat(body.path("latestScreen").isObject()).isTrue();
        assertCombatScreenContract(body.path("latestScreen"));
    }

    protected JsonNode assertApiErrorContract(MvcResult result, int expectedStatus) throws Exception {
        Assertions.assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        String responseBody = result.getResponse().getContentAsString();
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        JsonNode body = readJson(result);
        Assertions.assertThat(body.path("code").isTextual()).isTrue();
        Assertions.assertThat(body.path("category").isTextual()).isTrue();
        Assertions.assertThat(body.path("userMessage").isTextual()).isTrue();
        Assertions.assertThat(body.has("debugMessage")).isTrue();
        Assertions.assertThat(body.path("debugMessage").isTextual() || body.path("debugMessage").isNull()).isTrue();
        Assertions.assertThat(body.has("details")).isTrue();
        Assertions.assertThat(body.path("status").isInt()).isTrue();
        Assertions.assertThat(body.path("status").asInt()).isEqualTo(expectedStatus);
        Assertions.assertThat(body.has("path")).isTrue();
        Assertions.assertThat(body.path("path").isTextual() || body.path("path").isNull()).isTrue();
        return body;
    }
}

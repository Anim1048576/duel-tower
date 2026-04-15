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
        Assertions.assertThat(action.path("payloadTemplate").isObject() || action.path("payloadTemplate").isNull()).isTrue();
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

package com.api.blueprint.ai;

import com.api.blueprint.support.TestTags;
import org.json.JSONArray;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline unit tests: nothing here opens a network connection, so they also run in the
 * pull-request smoke suite.
 */
@Tag(TestTags.SMOKE)
@Tag(TestTags.REGRESSION)
class ClaudeClientTest {

    @Nested
    class ModelResolution {

        @Test
        void environmentVariableWinsOverProperty() {
            assertEquals("env-model", ClaudeClient.resolveModel("env-model", propertiesWithModel("file-model")));
        }

        @Test
        void propertyIsUsedWhenEnvironmentVariableIsAbsent() {
            assertEquals("file-model", ClaudeClient.resolveModel(null, propertiesWithModel("file-model")));
        }

        @Test
        void blankEnvironmentVariableFallsThroughToProperty() {
            assertEquals("file-model", ClaudeClient.resolveModel("  ", propertiesWithModel("file-model")));
        }

        @Test
        void defaultModelIsUsedWhenNothingIsConfigured() {
            assertEquals(ClaudeClient.DEFAULT_MODEL, ClaudeClient.resolveModel(null, new Properties()));
            assertEquals(ClaudeClient.DEFAULT_MODEL, ClaudeClient.resolveModel(null, propertiesWithModel(" ")));
            assertEquals(ClaudeClient.DEFAULT_MODEL, ClaudeClient.resolveModel(null, null));
        }

        @Test
        void surroundingWhitespaceIsTrimmed() {
            assertEquals("env-model", ClaudeClient.resolveModel(" env-model\n", new Properties()));
            assertEquals("file-model", ClaudeClient.resolveModel(null, propertiesWithModel(" file-model ")));
        }

        private Properties propertiesWithModel(String model) {
            Properties properties = new Properties();
            properties.setProperty(ClaudeClient.MODEL_PROPERTY, model);
            return properties;
        }
    }

    @Nested
    class FenceStripping {

        @Test
        void jsonFenceIsRemoved() {
            assertEquals("[{\"a\":1}]", ClaudeClient.stripMarkdownFences("```json\n[{\"a\":1}]\n```"));
        }

        @Test
        void untaggedFenceIsRemoved() {
            assertEquals("[]", ClaudeClient.stripMarkdownFences("```\n[]\n```"));
        }

        @Test
        void singleLineFenceIsRemoved() {
            assertEquals("[1,2]", ClaudeClient.stripMarkdownFences("```[1,2]```"));
        }

        @Test
        void proseAroundTheFenceIsDropped() {
            String answer = "Here are the cases:\n```json\n[1]\n```\nLet me know if you need more.";
            assertEquals("[1]", ClaudeClient.stripMarkdownFences(answer));
        }

        @Test
        void unterminatedFenceKeepsTheRestOfTheText() {
            assertEquals("[1]", ClaudeClient.stripMarkdownFences("```json\n[1]"));
        }

        @Test
        void plainTextIsOnlyTrimmed() {
            assertEquals("[1]", ClaudeClient.stripMarkdownFences("  [1]\n"));
        }

        @Test
        void nullBecomesEmpty() {
            assertEquals("", ClaudeClient.stripMarkdownFences(null));
        }

        @Test
        void fencedJsonArrayIsParsed() {
            Optional<JSONArray> parsed = ClaudeClient.parseJsonArray("```json\n[{\"testName\":\"t\"}]\n```");
            assertTrue(parsed.isPresent());
            assertEquals("t", parsed.get().getJSONObject(0).getString("testName"));
        }

        @Test
        void nonJsonAnswerYieldsEmpty() {
            assertTrue(ClaudeClient.parseJsonArray("I cannot help with that.").isEmpty());
        }
    }

    @Nested
    class ApiKey {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", ClaudeClient.PLACEHOLDER_API_KEY})
        void missingBlankOrPlaceholderKeyIsNotConfigured(String apiKey) {
            ClaudeClient client = new ClaudeClient(apiKey, ClaudeClient.DEFAULT_MODEL);

            assertFalse(client.isConfigured());
            assertTrue(client.complete("ping").isEmpty(), "an unconfigured client must not call the API");
            assertTrue(client.completeAsJsonArray("ping").isEmpty());
        }

        @Test
        void realLookingKeyIsConfigured() {
            assertTrue(new ClaudeClient("sk-ant-test", ClaudeClient.DEFAULT_MODEL).isConfigured());
        }
    }
}

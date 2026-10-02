package com.api.blueprint.ai;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Thin client for the Anthropic Messages API, shared by every AI-assisted
 * component of the framework.
 *
 * <p>All failure modes (no key, HTTP error, unparseable answer) surface as an
 * empty {@link Optional} so callers can fall back to their offline logic
 * instead of breaking the pipeline.</p>
 */
public final class ClaudeClient {

    /** Used when neither ANTHROPIC_MODEL nor anthropic.model is set. */
    public static final String DEFAULT_MODEL = "claude-sonnet-4-6";

    static final String MODEL_ENV_VAR = "ANTHROPIC_MODEL";
    static final String API_KEY_ENV_VAR = "ANTHROPIC_API_KEY";
    static final String MODEL_PROPERTY = "anthropic.model";
    static final String PLACEHOLDER_API_KEY = "your-api-key";

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";
    private static final String PROPERTIES_FILE = "test-env.properties";
    private static final int MAX_TOKENS = 4096;
    private static final Pattern FENCE_LANGUAGE_TAG = Pattern.compile("[\\w-]*\\s*");

    private final String apiKey;
    private final String model;

    public ClaudeClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    /**
     * Builds a client from the runtime environment: the key comes from
     * ANTHROPIC_API_KEY, the model from {@link #resolveModel(String, Properties)}.
     */
    public static ClaudeClient fromEnvironment() {
        return new ClaudeClient(
                System.getenv(API_KEY_ENV_VAR),
                resolveModel(System.getenv(MODEL_ENV_VAR), loadProperties()));
    }

    /**
     * Model precedence: ANTHROPIC_MODEL env var, then {@code anthropic.model}
     * from test-env.properties, then {@link #DEFAULT_MODEL}.
     */
    static String resolveModel(String envModel, Properties properties) {
        if (envModel != null && !envModel.isBlank()) {
            return envModel.trim();
        }
        String configured = properties == null ? null : properties.getProperty(MODEL_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return DEFAULT_MODEL;
    }

    public String getModel() {
        return model;
    }

    /** True only for a real-looking key; blank values and the sample placeholder do not count. */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.equals(PLACEHOLDER_API_KEY);
    }

    /**
     * Sends a single-turn prompt and returns the text of the first content block,
     * or empty when the client is not configured or the call fails.
     */
    public Optional<String> complete(String prompt) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        try {
            Response response = RestAssured.given()
                    .baseUri(API_URL)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .contentType(ContentType.JSON)
                    .body(buildRequestBody(prompt))
                    .post();

            if (response.statusCode() != 200) {
                System.err.println("Claude API (" + model + ") returned HTTP " + response.statusCode()
                        + ": " + response.asString());
                return Optional.empty();
            }
            return Optional.ofNullable(response.jsonPath().getString("content[0].text"));
        } catch (RuntimeException e) {
            System.err.println("Claude API call failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    /** Sends the prompt and parses the answer as a JSON array, tolerating markdown fences. */
    public Optional<JSONArray> completeAsJsonArray(String prompt) {
        return complete(prompt).flatMap(ClaudeClient::parseJsonArray);
    }

    static Optional<JSONArray> parseJsonArray(String responseText) {
        try {
            return Optional.of(new JSONArray(stripMarkdownFences(responseText)));
        } catch (JSONException e) {
            System.err.println("Claude response was not a JSON array: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Returns the body of the first markdown code fence (```json, ``` or any
     * other language tag), or the trimmed input when it contains no fence.
     */
    public static String stripMarkdownFences(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        int open = trimmed.indexOf("```");
        if (open < 0) {
            return trimmed;
        }

        int bodyStart = open + 3;
        int lineEnd = trimmed.indexOf('\n', bodyStart);
        if (lineEnd >= 0 && FENCE_LANGUAGE_TAG.matcher(trimmed.substring(bodyStart, lineEnd)).matches()) {
            bodyStart = lineEnd + 1;
        }

        int close = trimmed.lastIndexOf("```");
        int bodyEnd = close >= bodyStart ? close : trimmed.length();
        return trimmed.substring(bodyStart, bodyEnd).trim();
    }

    private String buildRequestBody(String prompt) {
        JSONObject message = new JSONObject()
                .put("role", "user")
                .put("content", prompt);
        return new JSONObject()
                .put("model", model)
                .put("max_tokens", MAX_TOKENS)
                .put("messages", new JSONArray().put(message))
                .toString();
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream input = ClaudeClient.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (input != null) {
                properties.load(input);
            }
        } catch (Exception e) {
            System.err.println("Could not load " + PROPERTIES_FILE + ": " + e.getMessage());
        }
        return properties;
    }
}

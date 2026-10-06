package com.api.blueprint.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Paths;

public class SchemaDriftDetector {

    private static final String DEFAULT_BASELINE = "src/test/resources/schemas/petstore-openapi.yaml";
    private static final String DEFAULT_CURRENT = "src/test/resources/schemas/petstore-openapi-current.yaml";

    /**
     * Usage: {@code SchemaDriftDetector [baselineSpec] [currentSpec]}. With no current spec
     * on disk there is nothing to compare, so the check passes without writing any file.
     */
    public static void main(String[] args) {
        String baselineSpecPath = args.length > 0 ? args[0] : DEFAULT_BASELINE;
        String currentSpecPath = args.length > 1 ? args[1] : DEFAULT_CURRENT;

        if (!Files.exists(Paths.get(baselineSpecPath))) {
            System.err.println(">>> FAILURE: Baseline spec not found at: " + baselineSpecPath);
            System.exit(1);
        }
        if (!Files.exists(Paths.get(currentSpecPath))) {
            System.out.println("No current spec at " + currentSpecPath + "; nothing to compare against the baseline.");
            return;
        }

        try {
            detectSchemaDrift(currentSpecPath, baselineSpecPath);
            System.out.println(">>> SUCCESS: Schema drift detection completed. No breaking changes found.");
        } catch (Exception e) {
            System.err.println(">>> FAILURE: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Analyzes two OpenAPI specs and flags schema breaking changes.
     * Designed to be run as a pre-test CI hook.
     */
    public static void detectSchemaDrift(String currentSpecPath, String baselineSpecPath) throws Exception {
        String currentSpec = new String(Files.readAllBytes(Paths.get(currentSpecPath)));
        String baselineSpec = new String(Files.readAllBytes(Paths.get(baselineSpecPath)));

        JSONArray breakingChanges = null;

        ClaudeClient claude = ClaudeClient.fromEnvironment();
        if (claude.isConfigured()) {
            System.out.println("Querying Claude (" + claude.getModel() + ") for semantic schema drift detection...");
            String prompt = "Act as an API Governance tool. Compare the baseline OpenAPI spec with the current spec. " +
                    "Identify ONLY backward-incompatible breaking changes (e.g., removed required fields, changed data types, removed endpoints). " +
                    "Return the result as a strict JSON array of objects with keys: 'endpoint', 'changeType', 'description'. " +
                    "Return an empty array if no breaking changes exist. \n\n" +
                    "Baseline:\n" + baselineSpec + "\n\nCurrent:\n" + currentSpec;
            breakingChanges = claude.completeAsJsonArray(prompt).orElse(null);
        }

        // Offline / Fallback verification
        if (breakingChanges == null) {
            System.out.println("Running offline structural drift detection...");
            breakingChanges = detectOfflineSchemaDrift(baselineSpec, currentSpec);
        }

        // Fail the pipeline immediately if structural drift is detected
        if (breakingChanges.length() > 0) {
            throw new IllegalStateException("Schema drift detector flagged breaking changes. Halting test execution.\n"
                    + breakingChanges.toString(2));
        }
    }

    /** Rule-based structural diff used when Claude is not configured or does not answer. */
    static JSONArray detectOfflineSchemaDrift(String baselineSpec, String currentSpec) {
        JSONArray changes = new JSONArray();
        for (OpenApiStructuralDiff.Finding finding : OpenApiStructuralDiff.compare(baselineSpec, currentSpec)) {
            changes.put(new JSONObject()
                    .put("endpoint", finding.endpoint())
                    .put("changeType", finding.changeType())
                    .put("description", finding.description()));
        }
        return changes;
    }
}

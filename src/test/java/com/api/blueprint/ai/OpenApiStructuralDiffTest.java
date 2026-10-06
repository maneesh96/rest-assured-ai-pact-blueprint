package com.api.blueprint.ai;

import com.api.blueprint.ai.OpenApiStructuralDiff.Finding;
import com.api.blueprint.support.TestTags;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.api.blueprint.ai.OpenApiStructuralDiff.CHANGED_TYPE;
import static com.api.blueprint.ai.OpenApiStructuralDiff.NEW_REQUIRED_PARAMETER;
import static com.api.blueprint.ai.OpenApiStructuralDiff.NEW_REQUIRED_REQUEST_FIELD;
import static com.api.blueprint.ai.OpenApiStructuralDiff.REMOVED_OPERATION;
import static com.api.blueprint.ai.OpenApiStructuralDiff.REMOVED_PATH;
import static com.api.blueprint.ai.OpenApiStructuralDiff.REMOVED_RESPONSE_FIELD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline unit tests for the structural OpenAPI diff, driven by the small fixtures under
 * src/test/resources/drift/. Nothing here opens a network connection.
 */
@Tag(TestTags.SMOKE)
@Tag(TestTags.REGRESSION)
class OpenApiStructuralDiffTest {

    private static final String BASELINE = resource("drift/baseline.yaml");

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class BreakingChanges {

        private List<Finding> findings;

        @BeforeAll
        void compareBaselineWithBreakingSpec() {
            findings = OpenApiStructuralDiff.compare(BASELINE, resource("drift/breaking.yaml"));
        }

        @Test
        void removedPathIsReported() {
            assertFinding("/store/inventory", REMOVED_PATH, "Path '/store/inventory' was removed.");
        }

        @Test
        void removedOperationIsReported() {
            assertFinding("DELETE /pet/{petId}", REMOVED_OPERATION, "Operation DELETE /pet/{petId} was removed.");
        }

        @Test
        void requestFieldThatBecameRequiredIsReported() {
            assertFinding("POST /pet", NEW_REQUIRED_REQUEST_FIELD,
                    "Field 'status' in the request body is now required.");
        }

        @Test
        void parameterThatBecameRequiredIsReported() {
            assertFinding("GET /pet/findByStatus", NEW_REQUIRED_PARAMETER, "Parameter query:status is now required.");
        }

        @Test
        void responseFieldRemovedFromReferencedComponentIsReported() {
            assertFinding("GET /pet/{petId}", REMOVED_RESPONSE_FIELD,
                    "Field 'category.name' in the 200 response was removed.");
        }

        @Test
        void responseFieldRemovedInsideArrayItemsIsReported() {
            assertFinding("GET /pet/findByStatus", REMOVED_RESPONSE_FIELD,
                    "Field '[].category.name' in the 200 response was removed.");
        }

        @Test
        void changedPropertyTypeIsReportedForRequestAndResponse() {
            assertFinding("POST /pet", CHANGED_TYPE,
                    "Field 'id' in the request body changed type from integer to string.");
            assertFinding("POST /pet", CHANGED_TYPE,
                    "Field 'id' in the 200 response changed type from integer to string.");
        }

        @Test
        void changedArrayItemTypeIsReported() {
            assertFinding("GET /pet/{petId}", CHANGED_TYPE,
                    "Field 'photoUrls[]' in the 200 response changed type from string to object.");
        }

        @Test
        void fieldRemovedFromRequestSchemaIsNotReportedAsRemovedResponseField() {
            assertTrue(findings.stream().noneMatch(f -> f.description().contains("request body was removed")),
                    () -> "Unexpected finding in " + findings);
        }

        @Test
        void everyBreakingChangeIsReportedExactlyOnce() {
            // 1 path + 1 operation + POST /pet (3 request + 3 response) + GET /pet/{petId} (3 response)
            // + GET /pet/findByStatus (1 parameter + 3 response)
            assertEquals(15, findings.size(), () -> "Findings: " + findings);
        }

        private void assertFinding(String endpoint, String changeType, String description) {
            Finding expected = new Finding(endpoint, changeType, description);
            assertTrue(findings.contains(expected), () -> "Missing " + expected + " in " + findings);
        }
    }

    @Nested
    class CompatibleChanges {

        @Test
        void identicalSpecsHaveNoFindings() {
            assertEquals(List.of(), OpenApiStructuralDiff.compare(BASELINE, BASELINE));
        }

        @Test
        void additiveAndRelaxingChangesAreNotReported() {
            assertEquals(List.of(), OpenApiStructuralDiff.compare(BASELINE, resource("drift/compatible.yaml")));
        }

        @Test
        void committedPetstoreSpecParsesAndMatchesItself() {
            String petstore = resource("schemas/petstore-openapi.yaml");
            assertEquals(List.of(), OpenApiStructuralDiff.compare(petstore, petstore));
        }
    }

    @Nested
    class InvalidInput {

        @Test
        void specThatIsNotAMappingIsRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> OpenApiStructuralDiff.compare(BASELINE, "- just\n- a list\n"));
        }

        @Test
        void unresolvableReferenceIsRejected() {
            String dangling = BASELINE.replace("'#/components/schemas/Category'", "'#/components/schemas/Missing'");
            assertThrows(IllegalArgumentException.class, () -> OpenApiStructuralDiff.compare(BASELINE, dangling));
        }
    }

    @Test
    void detectorOfflineModeReturnsFindingsAsJson() {
        JSONArray changes = SchemaDriftDetector.detectOfflineSchemaDrift(BASELINE, resource("drift/breaking.yaml"));

        assertEquals(15, changes.length());
        JSONObject first = changes.getJSONObject(0);
        assertTrue(first.has("endpoint") && first.has("changeType") && first.has("description"),
                () -> "Unexpected shape: " + first);
    }

    private static String resource(String path) {
        try (InputStream input = OpenApiStructuralDiffTest.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Test resource not found: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

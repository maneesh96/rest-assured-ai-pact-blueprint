package com.api.blueprint.ai;

import com.api.blueprint.config.ApiConfig;
import com.api.blueprint.support.TestTags;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Generates adversarial POST /pet cases from the OpenAPI spec with Claude, or
 * from a fixed offline set when no API key is configured.
 *
 * <p>Offline cases that send input the spec forbids live in a separate factory
 * tagged "known-defect": they expect 400, the live petstore accepts them, and
 * they are excluded from the default run (see KNOWN_DEFECTS.md).
 */
@Tag(TestTags.REGRESSION)
public class AiDrivenDynamicTest {

    @TestFactory
    public Collection<DynamicTest> generateIntelligentTestsFromSpec() {
        // 1. Resolve path to OpenAPI spec
        String specPath = "src/test/resources/schemas/petstore-openapi.yaml";
        File specFile = new File(specPath);
        if (!specFile.exists()) {
            specPath = "../src/test/resources/schemas/petstore-openapi.yaml";
        }

        String openApiSpec = "";
        try {
            openApiSpec = new String(Files.readAllBytes(Paths.get(specPath)));
        } catch (Exception e) {
            System.err.println("Could not read OpenAPI spec: " + e.getMessage());
        }

        JSONArray generatedTestsArray = null;

        // 2. Query Claude if an API key is configured, else fall back to local payloads
        ClaudeClient claude = ClaudeClient.fromEnvironment();
        if (claude.isConfigured()) {
            String prompt = "You are a Senior API Security and QA Automation Engineer. Analyze the following OpenAPI specification. " +
                    "Generate 5 highly complex, adversarial JSON payloads for the POST /pet endpoint targeting boundary conditions, " +
                    "semantic logic errors, and security edge cases (e.g., extremely long strings, SQLi characters in string fields). " +
                    "Return the response strictly as a JSON array of objects with keys: 'testName', 'payload', 'expectedStatusCode'. " +
                    "Do not include markdown or explanations. Specification:\n" + openApiSpec;
            generatedTestsArray = claude.completeAsJsonArray(prompt).orElse(null);
        }

        // 3. Fallback to offline generation if API key is missing or request failed
        if (generatedTestsArray == null) {
            System.out.println("Using offline fallback test case generator for Dynamic Tests...");
            generatedTestsArray = getFallbackTestCases();
        }

        return toDynamicTests("AI Generated (Dynamic): ", generatedTestsArray);
    }

    @Tag(TestTags.KNOWN_DEFECT)
    @TestFactory
    public Collection<DynamicTest> specViolationsMustBeRejected() {
        return toDynamicTests("Spec violation (Dynamic): ", getSpecViolationTestCases());
    }

    private static Collection<DynamicTest> toDynamicTests(String prefix, JSONArray testCases) {
        Collection<DynamicTest> dynamicTests = new ArrayList<>();
        for (int i = 0; i < testCases.length(); i++) {
            JSONObject testCase = testCases.getJSONObject(i);
            String testName = testCase.getString("testName");
            JSONObject payload = testCase.getJSONObject("payload");
            int expectedStatus = testCase.getInt("expectedStatusCode");

            dynamicTests.add(DynamicTest.dynamicTest(prefix + testName, () -> {
                int actualStatus = given()
                        .spec(ApiConfig.getBaseRequestSpec())
                        .body(payload.toString())
                .when()
                        .post("/pet")
                .then()
                        .extract().statusCode();

                assertEquals(expectedStatus, actualStatus, "Unexpected HTTP status for: " + testName);
            }));
        }
        return dynamicTests;
    }

    private JSONArray getFallbackTestCases() {
        JSONArray cases = new JSONArray();

        // Extreme String Injection (SQL Injection characters)
        JSONObject payload1 = new JSONObject()
                .put("id", 999901)
                .put("name", "Maximus'; DROP TABLE pets;--")
                .put("photoUrls", new JSONArray().put("http://example.com/image.jpg"))
                .put("status", "available");
        cases.put(new JSONObject()
                .put("testName", "SQL Injection Vector in Name field")
                .put("payload", payload1)
                .put("expectedStatusCode", 200));

        // Custom Category Object with Negative Category ID
        JSONObject categoryJson = new JSONObject()
                .put("id", -1)
                .put("name", "Feline");
        JSONObject payload4 = new JSONObject()
                .put("id", 999904)
                .put("name", "Mittens")
                .put("category", categoryJson)
                .put("photoUrls", new JSONArray().put("http://example.com/image.jpg"))
                .put("status", "available");
        cases.put(new JSONObject()
                .put("testName", "Negative ID inside Nested Category Object")
                .put("payload", payload4)
                .put("expectedStatusCode", 200));

        // Extremely Long Pet Name (Overflow Boundary Test)
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            longName.append("a");
        }
        JSONObject payload5 = new JSONObject()
                .put("id", 999905)
                .put("name", longName.toString())
                .put("photoUrls", new JSONArray().put("http://example.com/image.jpg"))
                .put("status", "available");
        cases.put(new JSONObject()
                .put("testName", "Extremely Long Name Buffer Validation")
                .put("payload", payload5)
                .put("expectedStatusCode", 200));

        // Empty photoUrls array
        JSONObject payload6 = new JSONObject()
                .put("id", 999906)
                .put("name", "NoPhotoPet")
                .put("photoUrls", new JSONArray())
                .put("status", "available");
        cases.put(new JSONObject()
                .put("testName", "Empty photoUrls array validation")
                .put("payload", payload6)
                .put("expectedStatusCode", 200));

        // Large ID value (Long boundary test)
        JSONObject payload8 = new JSONObject()
                .put("id", 9223372036854775807L)
                .put("name", "HugeIdPet")
                .put("photoUrls", new JSONArray().put("http://example.com/image.jpg"))
                .put("status", "available");
        cases.put(new JSONObject()
                .put("testName", "Max Long ID boundary validation")
                .put("payload", payload8)
                .put("expectedStatusCode", 200));

        // Nested tags list validation with missing tag name
        JSONArray tagsJson = new JSONArray();
        tagsJson.put(new JSONObject().put("id", 5));
        JSONObject payload10 = new JSONObject()
                .put("id", 999910)
                .put("name", "TagMissingNamePet")
                .put("photoUrls", new JSONArray().put("http://example.com/image.jpg"))
                .put("tags", tagsJson)
                .put("status", "available");
        cases.put(new JSONObject()
                .put("testName", "Missing tag name in nested tags array")
                .put("payload", payload10)
                .put("expectedStatusCode", 200));

        return cases;
    }

    // Input the OpenAPI spec forbids: "name" and "photoUrls" are required, photoUrls is
    // an array and status is an enum. The spec answers 400 "Invalid input" for these.
    private static JSONArray getSpecViolationTestCases() {
        JSONArray cases = new JSONArray();

        // Validation Failure - Missing required fields (Name is required in schema)
        JSONObject payload2 = new JSONObject()
                .put("id", 999902)
                .put("photoUrls", new JSONArray().put("http://example.com/image.jpg"))
                .put("status", "available");
        cases.put(new JSONObject()
                .put("testName", "Missing Required Name Field")
                .put("payload", payload2)
                .put("expectedStatusCode", 400));

        // Invalid Array Data Type
        JSONObject payload3 = new JSONObject()
                .put("id", 999903)
                .put("name", "Rex")
                .put("photoUrls", "not_an_array")
                .put("status", "available");
        cases.put(new JSONObject()
                .put("testName", "Invalid Data Type - photoUrls String instead of Array")
                .put("payload", payload3)
                .put("expectedStatusCode", 400));

        // Invalid status enum value
        JSONObject payload7 = new JSONObject()
                .put("id", 999907)
                .put("name", "EnumTestPet")
                .put("photoUrls", new JSONArray().put("http://example.com/image.jpg"))
                .put("status", "super-available");
        cases.put(new JSONObject()
                .put("testName", "Invalid Status Enum value validation")
                .put("payload", payload7)
                .put("expectedStatusCode", 400));

        // SQL syntax injection in status field
        JSONObject payload9 = new JSONObject()
                .put("id", 999909)
                .put("name", "SafeName")
                .put("photoUrls", new JSONArray().put("http://example.com/image.jpg"))
                .put("status", "sold; UPDATE pet SET name='hacked'");
        cases.put(new JSONObject()
                .put("testName", "SQL Injection in Enum Status Field")
                .put("payload", payload9)
                .put("expectedStatusCode", 400));

        return cases;
    }
}

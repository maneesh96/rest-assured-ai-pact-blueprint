package com.api.blueprint.ai;

import com.api.blueprint.config.ApiConfig;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;

import static io.restassured.RestAssured.given;

public class AiDrivenDynamicTestGenerator {

    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_KEY = System.getenv("ANTHROPIC_API_KEY");

    @TestFactory
    public Collection<DynamicTest> generateIntelligentTestsFromSpec() throws Exception {
        Collection<DynamicTest> dynamicTests = new ArrayList<>();
        
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

        // 2. Query LLM if API key is provided, else fall back to local mock payloads
        if (API_KEY != null && !API_KEY.isBlank() && !API_KEY.equals("your-api-key")) {
            try {
                String prompt = "You are a Senior API Security and QA Automation Engineer. Analyze the following OpenAPI specification. " +
                        "Generate 5 highly complex, adversarial JSON payloads for the POST /pet endpoint targeting boundary conditions, " +
                        "semantic logic errors, and security edge cases (e.g., extremely long strings, SQLi characters in string fields). " +
                        "Return the response strictly as a JSON array of objects with keys: 'testName', 'payload', 'expectedStatusCode'. " +
                        "Do not include markdown or explanations. Specification:\n" + openApiSpec;

                Response aiResponse = RestAssured.given()
                        .baseUri(CLAUDE_API_URL)
                        .header("x-api-key", API_KEY)
                        .header("anthropic-version", "2023-06-01")
                        .contentType(ContentType.JSON)
                        .body(buildClaudePayload(prompt))
                        .post();

                if (aiResponse.statusCode() == 200) {
                    String content = aiResponse.jsonPath().getString("content[0].text");
                    if (content.contains("```json")) {
                        content = content.substring(content.indexOf("```json") + 7);
                        content = content.substring(0, content.lastIndexOf("```"));
                    } else if (content.contains("```")) {
                        content = content.substring(content.indexOf("```") + 3);
                        content = content.substring(0, content.lastIndexOf("```"));
                    }
                    generatedTestsArray = new JSONArray(content.trim());
                } else {
                    System.err.println("Claude API returned error code " + aiResponse.statusCode() + ": " + aiResponse.asString());
                }
            } catch (Exception e) {
                System.err.println("Failed to get response from Claude API: " + e.getMessage());
            }
        }

        // 3. Fallback to offline generation if API key is missing or request failed
        if (generatedTestsArray == null) {
            System.out.println("Using offline fallback test case generator for Dynamic Tests...");
            generatedTestsArray = getFallbackTestCases();
        }

        // 4. Map test cases to JUnit 5 Dynamic Tests
        for (int i = 0; i < generatedTestsArray.length(); i++) {
            JSONObject testCase = generatedTestsArray.getJSONObject(i);
            String testName = testCase.getString("testName");
            JSONObject payload = testCase.getJSONObject("payload");
            int expectedStatus = testCase.getInt("expectedStatusCode");

            DynamicTest test = DynamicTest.dynamicTest("AI Generated (Dynamic): " + testName, () -> {
                given()
                        .spec(ApiConfig.getBaseRequestSpec())
                        .body(payload.toString())
                .when()
                        .post("/pet")
                .then()
                        .statusCode(expectedStatus);
            });
            dynamicTests.add(test);
        }

        return dynamicTests;
    }

    private String buildClaudePayload(String prompt) {
        JSONObject body = new JSONObject();
        body.put("model", "claude-3-5-sonnet-20241022");
        body.put("max_tokens", 4096);
        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", prompt);
        messages.put(message);
        body.put("messages", messages);
        return body.toString();
    }

    private JSONArray getFallbackTestCases() {
        JSONArray cases = new JSONArray();

        // Case 1: Extreme String Injection (SQL Injection characters)
        JSONObject payload1 = new JSONObject()
                .put("id", 999901)
                .put("name", "Maximus'; DROP TABLE pets;--")
                .put("photoUrls", new JSONArray().put("http://example.com/image.jpg"))
                .put("status", "available");
        cases.put(new JSONObject()
                .put("testName", "SQL Injection Vector in Name field")
                .put("payload", payload1)
                .put("expectedStatusCode", 200));

        // Case 2: Validation Failure - Missing required fields (Name is required in schema)
        JSONObject payload2 = new JSONObject()
                .put("id", 999902)
                .put("photoUrls", new JSONArray().put("http://example.com/image.jpg"))
                .put("status", "available");
        cases.put(new JSONObject()
                .put("testName", "Missing Required Name Field")
                .put("payload", payload2)
                .put("expectedStatusCode", 400));

        // Case 3: Invalid Array Data Type
        JSONObject payload3 = new JSONObject()
                .put("id", 999903)
                .put("name", "Rex")
                .put("photoUrls", "not_an_array")
                .put("status", "available");
        cases.put(new JSONObject()
                .put("testName", "Invalid Data Type - photoUrls String instead of Array")
                .put("payload", payload3)
                .put("expectedStatusCode", 400));

        // Case 4: Custom Category Object with Negative Category ID
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

        // Case 5: Extremely Long Pet Name (Overflow Boundary Test)
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

        // Case 6: Empty photoUrls array
        JSONObject payload6 = new JSONObject()
                .put("id", 999906)
                .put("name", "NoPhotoPet")
                .put("photoUrls", new JSONArray())
                .put("status", "available");
        cases.put(new JSONObject()
                .put("testName", "Empty photoUrls array validation")
                .put("payload", payload6)
                .put("expectedStatusCode", 200));

        // Case 7: Invalid status enum value
        JSONObject payload7 = new JSONObject()
                .put("id", 999907)
                .put("name", "EnumTestPet")
                .put("photoUrls", new JSONArray().put("http://example.com/image.jpg"))
                .put("status", "super-available");
        cases.put(new JSONObject()
                .put("testName", "Invalid Status Enum value validation")
                .put("payload", payload7)
                .put("expectedStatusCode", 400));

        // Case 8: Large ID value (Long boundary test)
        JSONObject payload8 = new JSONObject()
                .put("id", 9223372036854775807L)
                .put("name", "HugeIdPet")
                .put("photoUrls", new JSONArray().put("http://example.com/image.jpg"))
                .put("status", "available");
        cases.put(new JSONObject()
                .put("testName", "Max Long ID boundary validation")
                .put("payload", payload8)
                .put("expectedStatusCode", 200));

        // Case 9: SQL syntax injection in status field
        JSONObject payload9 = new JSONObject()
                .put("id", 999909)
                .put("name", "SafeName")
                .put("photoUrls", new JSONArray().put("http://example.com/image.jpg"))
                .put("status", "sold; UPDATE pet SET name='hacked'");
        cases.put(new JSONObject()
                .put("testName", "SQL Injection in Enum Status Field")
                .put("payload", payload9)
                .put("expectedStatusCode", 400));

        // Case 10: Nested tags list validation with missing tag name
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
}

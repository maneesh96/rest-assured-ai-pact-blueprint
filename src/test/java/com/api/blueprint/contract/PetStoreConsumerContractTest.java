package com.api.blueprint.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.DslPart;
import au.com.dius.pact.consumer.dsl.PactDslJsonArray;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.PactSpecVersion;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "SwaggerPetstoreProvider", port = "8888", pactVersion = PactSpecVersion.V3)
public class PetStoreConsumerContractTest {

    @Pact(consumer = "PetStoreJavaConsumer", provider = "SwaggerPetstoreProvider")
    public RequestResponsePact getPetByIdContract(PactDslWithProvider builder) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        DslPart responseBody = new PactDslJsonBody()
                .numberType("id", 123L)
                .stringType("name", "Buddy")
                .stringMatcher("status", "^(available|pending|sold)$", "available")
                .minArrayLike("photoUrls", 1)
                    .stringType("http://image.url")
                .closeArray()
                .object("category")
                    .numberType("id", 1)
                    .stringType("name", "Dogs")
                .closeObject();

        return builder
                .given("Pet with ID 123 exists")
                .uponReceiving("A request to retrieve pet 123")
                .path("/v2/pet/123")
                .method("GET")
                .willRespondWith()
                .status(200)
                .headers(headers)
                .body(responseBody)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getPetByIdContract")
    public void testGetPetByIdContract(MockServer mockServer) {
        RestAssured.baseURI = mockServer.getUrl();

        given()
                .header("Accept", "application/json")
        .when()
                .get("/v2/pet/123")
        .then()
                .statusCode(200)
                .body("name", equalTo("Buddy"))
                .body("category.name", equalTo("Dogs"))
                .body("id", notNullValue());
    }

    @Pact(consumer = "PetStoreJavaConsumer", provider = "SwaggerPetstoreProvider")
    public RequestResponsePact createPetContract(PactDslWithProvider builder) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        DslPart requestBody = new PactDslJsonBody()
                .stringType("name", "Maximus")
                .stringMatcher("status", "^(available|pending|sold)$", "available")
                .array("photoUrls")
                    .string("http://image.url")
                .closeArray();

        DslPart responseBody = new PactDslJsonBody()
                .numberType("id")
                .stringType("name", "Maximus")
                .stringMatcher("status", "^(available|pending|sold)$", "available")
                .minArrayLike("photoUrls", 1)
                    .stringType("http://image.url")
                .closeArray();

        return builder
                .given("Pet store allows adding pets")
                .uponReceiving("A POST request to create a new pet")
                .path("/v2/pet")
                .method("POST")
                .headers(headers)
                .body(requestBody)
                .willRespondWith()
                .status(200)
                .headers(headers)
                .body(responseBody)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "createPetContract")
    public void testCreatePetContract(MockServer mockServer) {
        RestAssured.baseURI = mockServer.getUrl();

        String payload = "{\"name\":\"Maximus\",\"status\":\"available\",\"photoUrls\":[\"http://image.url\"]}";

        given()
                .header("Content-Type", "application/json")
                .body(payload)
        .when()
                .post("/v2/pet")
        .then()
                .statusCode(200)
                .body("name", equalTo("Maximus"))
                .body("id", notNullValue());
    }

    @Pact(consumer = "PetStoreJavaConsumer", provider = "SwaggerPetstoreProvider")
    public RequestResponsePact findPetsByStatusContract(PactDslWithProvider builder) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        DslPart responseBody = PactDslJsonArray.arrayEachLike()
                .numberType("id", 456L)
                .stringType("name", "Rover")
                .stringType("status", "available")
                .closeObject();

        return builder
                .given("Pets with status available exist")
                .uponReceiving("A request to find pets by status available")
                .path("/v2/pet/findByStatus")
                .query("status=available")
                .method("GET")
                .willRespondWith()
                .status(200)
                .headers(headers)
                .body(responseBody)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "findPetsByStatusContract")
    public void testFindPetsByStatusContract(MockServer mockServer) {
        RestAssured.baseURI = mockServer.getUrl();

        given()
                .header("Accept", "application/json")
                .queryParam("status", "available")
        .when()
                .get("/v2/pet/findByStatus")
        .then()
                .statusCode(200)
                .body("[0].name", equalTo("Rover"))
                .body("[0].status", equalTo("available"));
    }
}

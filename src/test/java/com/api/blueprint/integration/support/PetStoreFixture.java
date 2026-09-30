package com.api.blueprint.integration.support;

import com.api.blueprint.config.ApiConfig;
import com.api.blueprint.models.Category;
import com.api.blueprint.models.Order;
import com.api.blueprint.models.Pet;
import com.api.blueprint.models.User;
import io.restassured.response.Response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static io.restassured.RestAssured.given;

/**
 * Per-test data factory for the public Swagger Petstore.
 *
 * <p>Every resource gets an id or username that is unique to this run, so tests never
 * depend on each other or on data other people left in the shared sandbox. Everything
 * created through this fixture is recorded and removed again by {@link #cleanUp()}.
 */
public class PetStoreFixture {

    /** Demo key documented by Swagger Petstore; it guards nothing and is not a secret. */
    public static final String API_KEY = "special-key";

    private static final int READ_ATTEMPTS = 5;
    private static final long READ_INTERVAL_MS = 1_000L;

    private final List<Long> petIds = new ArrayList<>();
    private final List<Long> orderIds = new ArrayList<>();
    private final List<String> usernames = new ArrayList<>();

    /** Random positive id above the int range, which is what Petstore ids look like in practice. */
    public static long uniqueId() {
        return ThreadLocalRandom.current().nextLong(10_000_000_000L, 9_000_000_000_000_000L);
    }

    public static String uniqueUsername(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public Pet newPet(String name, String status) {
        return new Pet(uniqueId(), new Category(1, "Dogs"), name,
                new String[]{"https://example.com/" + name + ".png"}, Collections.emptyList(), status);
    }

    public Order newOrder() {
        return new Order(uniqueId(), uniqueId(), 5, "2026-07-15T19:43:58.000Z", "placed", false);
    }

    public User newUser() {
        String username = uniqueUsername("qa_user");
        return new User(uniqueId(), username, "QA", "Tester", username + "@example.com", "pass123", "555-5555", 1);
    }

    /** Records a pet created by the test itself so it is deleted after the test. */
    public void trackPet(long petId) {
        petIds.add(petId);
    }

    public void trackOrder(long orderId) {
        orderIds.add(orderId);
    }

    public void trackUser(String username) {
        usernames.add(username);
    }

    /** Creates a pet as a precondition and fails fast if the API rejects it. */
    public Pet givenPet(String name, String status) {
        Pet pet = newPet(name, status);
        trackPet(pet.getId());
        given().spec(ApiConfig.getBaseRequestSpec()).body(pet)
                .when().post("/pet")
                .then().statusCode(200);
        return pet;
    }

    public Order givenOrder() {
        Order order = newOrder();
        trackOrder(order.getId());
        given().spec(ApiConfig.getBaseRequestSpec()).body(order)
                .when().post("/store/order")
                .then().statusCode(200);
        return order;
    }

    public User givenUser() {
        User user = newUser();
        trackUser(user.getUsername());
        given().spec(ApiConfig.getBaseRequestSpec()).body(user)
                .when().post("/user")
                .then().statusCode(200);
        return user;
    }

    /**
     * GETs {@code path} until it answers with {@code expectedStatus} or the attempts run out,
     * and returns the last response for the caller to assert on.
     *
     * <p>The public Petstore runs several instances behind a load balancer, so a write is not
     * always visible to the very next read. Waiting briefly for convergence keeps tests stable
     * without weakening them: the caller still asserts the final status and body.
     */
    public Response getWhenStatus(int expectedStatus, String path, Object... pathParams) {
        Response response = null;
        for (int attempt = 1; attempt <= READ_ATTEMPTS; attempt++) {
            response = given().spec(ApiConfig.getBaseRequestSpec()).when().get(path, pathParams);
            if (response.statusCode() == expectedStatus || attempt == READ_ATTEMPTS) {
                break;
            }
            pause();
        }
        return response;
    }

    /** Best-effort removal of everything this test created; a resource the test already deleted is fine. */
    public void cleanUp() {
        petIds.forEach(id -> given().baseUri(ApiConfig.getBaseUrl()).header("api_key", API_KEY)
                .delete("/pet/{petId}", id));
        orderIds.forEach(id -> given().baseUri(ApiConfig.getBaseUrl())
                .delete("/store/order/{orderId}", id));
        usernames.forEach(name -> given().baseUri(ApiConfig.getBaseUrl())
                .delete("/user/{username}", name));
        petIds.clear();
        orderIds.clear();
        usernames.clear();
    }

    private static void pause() {
        try {
            Thread.sleep(READ_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Petstore to converge", e);
        }
    }
}

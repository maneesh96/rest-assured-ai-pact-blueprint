package com.api.blueprint.integration;

import com.api.blueprint.config.ApiConfig;
import com.api.blueprint.models.Category;
import com.api.blueprint.models.Order;
import com.api.blueprint.models.Pet;
import com.api.blueprint.models.User;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class PetStoreDdtTest {

    // --- Scenario Set 1: Pet Status Finder Validation (10 Test Cases) ---
    @ParameterizedTest(name = "DDT-Status: Querying status ''{0}'' should return code {1}")
    @CsvSource({
            "available, 200",
            "pending, 200",
            "sold, 200",
            "'available,pending', 200",
            "'pending,sold', 200",
            "'available,sold', 200",
            "'available,pending,sold', 200",
            "unknown, 200",
            "invalid_enum_val, 200",
            "null_placeholder, 200"
    })
    @DisplayName("Verify Pet Find By Status Endpoint with Various Query Inputs")
    public void findPetsByStatus_Validation(String status, int expectedStatusCode) {
        String finalStatus = "null_placeholder".equals(status) ? null : status;
        
        var request = given().spec(ApiConfig.getBaseRequestSpec());
        if (finalStatus != null) {
            request.queryParam("status", finalStatus);
        }

        request.when()
                .get("/pet/findByStatus")
        .then()
                .statusCode(expectedStatusCode)
                .body("$", is(notNullValue()));
    }

    // --- Scenario Set 2: Pet Creation Payload Validation (20 Test Cases) ---
    @ParameterizedTest(name = "DDT-Pet: Creating pet ''{0}'' with status ''{1}'' should return status {2}")
    @MethodSource("providePetCreationData")
    @DisplayName("Verify Pet Creation with Diverse Attributes & Boundaries")
    public void createPet_PayloadValidation(String name, String status, int expectedStatusCode, Pet petPayload) {
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .body(petPayload)
        .when()
                .post("/pet")
        .then()
                .statusCode(expectedStatusCode);
    }

    private static Stream<Arguments> providePetCreationData() {
        return Stream.of(
                // 1-5: Happy paths with different statuses
                Arguments.of("Max", "available", 200, createPetPojo(99001L, "Max", "available")),
                Arguments.of("Bella", "pending", 200, createPetPojo(99002L, "Bella", "pending")),
                Arguments.of("Rocky", "sold", 200, createPetPojo(99003L, "Rocky", "sold")),
                Arguments.of("Luna", "available", 200, createPetPojo(99004L, "Luna", "available")),
                Arguments.of("Charlie", "pending", 200, createPetPojo(99005L, "Charlie", "pending")),

                // 6-10: Special characters in name
                Arguments.of("Max@123", "available", 200, createPetPojo(99006L, "Max@123", "available")),
                Arguments.of("A&B", "available", 200, createPetPojo(99007L, "A&B", "available")),
                Arguments.of("Sparky-Doo", "available", 200, createPetPojo(99008L, "Sparky-Doo", "available")),
                Arguments.of("Shadow_1", "available", 200, createPetPojo(99009L, "Shadow_1", "available")),
                Arguments.of("O'Connor", "available", 200, createPetPojo(99010L, "O'Connor", "available")),

                // 11-15: Numeric name / long name / empty name
                Arguments.of("12345", "available", 200, createPetPojo(99011L, "12345", "available")),
                Arguments.of("VeryLongPetNameThatExceedsStandardExpectationsButShouldBeHandledGracefullyByTheDatabaseSystemWithoutTruncation", "available", 200, createPetPojo(99012L, "VeryLongPetNameThatExceedsStandardExpectationsButShouldBeHandledGracefullyByTheDatabaseSystemWithoutTruncation", "available")),
                Arguments.of("", "available", 200, createPetPojo(99013L, "", "available")),
                Arguments.of("NullNamePet", "available", 200, createPetPojo(99014L, null, "available")),
                Arguments.of("StatusNullPet", "available", 200, createPetPojo(99015L, "StatusNullPet", null)),

                // 16-20: Missing elements / Custom categories
                Arguments.of("MaxNoUrls", "available", 200, new Pet(99016L, new Category(1, "Dogs"), "MaxNoUrls", new String[]{}, Collections.emptyList(), "available")),
                Arguments.of("MaxNullUrls", "available", 200, new Pet(99017L, new Category(1, "Dogs"), "MaxNullUrls", null, Collections.emptyList(), "available")),
                Arguments.of("NegativeIdPet", "available", 200, createPetPojo(-12345L, "NegativeIdPet", "available")),
                Arguments.of("MaxNoCategory", "available", 200, new Pet(99019L, null, "MaxNoCategory", new String[]{"http://img.url"}, Collections.emptyList(), "available")),
                Arguments.of("PetWithEmojiName🐶", "available", 200, createPetPojo(99020L, "PetWithEmojiName🐶", "available"))
        );
    }

    private static Pet createPetPojo(Long id, String name, String status) {
        return new Pet(id, new Category(1, "Dogs"), name, new String[]{"http://images.com/pet.jpg"}, Collections.emptyList(), status);
    }

    // --- Scenario Set 3: Store Order Payload Validation (20 Test Cases) ---
    @ParameterizedTest(name = "DDT-Order: Placing order petId={0}, quantity={1}, status={2} should return status {3}")
    @MethodSource("provideOrderCreationData")
    @DisplayName("Verify Order Creation Validation and Boundaries")
    public void storeOrder_Validation(Long petId, int quantity, String status, int expectedStatusCode, Order orderPayload) {
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .body(orderPayload)
        .when()
                .post("/store/order")
        .then()
                .statusCode(expectedStatusCode);
    }

    private static Stream<Arguments> provideOrderCreationData() {
        return Stream.of(
                // 1-5: Valid orders with quantities
                Arguments.of(1L, 1, "placed", 200, createOrderPojo(9901L, 1L, 1, "placed", true)),
                Arguments.of(2L, 5, "placed", 200, createOrderPojo(9902L, 2L, 5, "placed", false)),
                Arguments.of(3L, 99, "approved", 200, createOrderPojo(9903L, 3L, 99, "approved", true)),
                Arguments.of(4L, 1000, "delivered", 200, createOrderPojo(9904L, 4L, 1000, "delivered", false)),
                Arguments.of(5L, 2, "placed", 200, createOrderPojo(9905L, 5L, 2, "placed", true)),

                // 6-10: Boundary quantities
                Arguments.of(6L, 0, "placed", 200, createOrderPojo(9906L, 6L, 0, "placed", true)),
                Arguments.of(7L, -1, "placed", 200, createOrderPojo(9907L, 7L, -1, "placed", true)), // API accepts negative values
                Arguments.of(8L, -100, "placed", 200, createOrderPojo(9908L, 8L, -100, "placed", false)),
                Arguments.of(9L, 999999, "placed", 200, createOrderPojo(9909L, 9L, 999999, "placed", true)),
                Arguments.of(10L, 2, "unknown", 200, createOrderPojo(9910L, 10L, 2, "unknown", false)),

                // 11-15: Missing or extreme fields
                Arguments.of(null, 1, "placed", 200, createOrderPojo(9911L, null, 1, "placed", true)),
                Arguments.of(12L, 1, null, 200, createOrderPojo(9912L, 12L, 1, null, true)),
                Arguments.of(13L, 1, "placed", 200, new Order(9913L, 13L, 1, null, "placed", null)),
                Arguments.of(14L, 1, "placed", 200, createOrderPojo(-9914L, 14L, 1, "placed", true)),
                Arguments.of(-1L, 1, "placed", 200, createOrderPojo(9915L, -1L, 1, "placed", true)),

                // 16-20: Different date/time schemas and format boundaries
                Arguments.of(16L, 1, "placed", 200, new Order(9916L, 16L, 1, "2026-07-15T19:43:58.000Z", "placed", true)),
                Arguments.of(17L, 1, "placed", 200, new Order(9917L, 17L, 1, "2026-07-15", "placed", true)), // API parses valid partial ISO dates as 200
                Arguments.of(18L, 1, "placed", 500, new Order(9918L, 18L, 1, "invalid-date-format", "placed", true)),
                Arguments.of(19L, 1, "placed", 200, new Order(9919L, 19L, 1, "", "placed", true)), // API treats empty string as empty/default date (200)
                Arguments.of(20L, 1, "placed", 200, new Order(9920L, 20L, 1, "2999-12-31T23:59:59.999+00:00", "approved", false))
        );
    }

    private static Order createOrderPojo(Long id, Long petId, int quantity, String status, boolean complete) {
        return new Order(id, petId, quantity, "2026-07-15T14:14:00.000Z", status, complete);
    }

    // --- Scenario Set 4: User Creation Payload Validation (20 Test Cases) ---
    @ParameterizedTest(name = "DDT-User: Creating user ''{0}'' should return status {1}")
    @MethodSource("provideUserCreationData")
    @DisplayName("Verify User Creation Endpoint and Data Validation Boundaries")
    public void createUser_Validation(String username, int expectedStatusCode, User userPayload) {
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .body(userPayload)
        .when()
                .post("/user")
        .then()
                .statusCode(expectedStatusCode);
    }

    private static Stream<Arguments> provideUserCreationData() {
        return Stream.of(
                // 1-5: Valid User creation happy paths
                Arguments.of("user_1", 200, createUserPojo(9901L, "user_1", "pass1")),
                Arguments.of("user_2", 200, createUserPojo(9902L, "user_2", "pass2")),
                Arguments.of("user_3", 200, createUserPojo(9903L, "user_3", "pass3")),
                Arguments.of("user_4", 200, createUserPojo(9904L, "user_4", "pass4")),
                Arguments.of("user_5", 200, createUserPojo(9905L, "user_5", "pass5")),

                // 6-10: Edge case usernames
                Arguments.of("u", 200, createUserPojo(9906L, "u", "pass6")),
                Arguments.of("user-with-hyphens", 200, createUserPojo(9907L, "user-with-hyphens", "pass7")),
                Arguments.of("user_with_underscores", 200, createUserPojo(9908L, "user_with_underscores", "pass8")),
                Arguments.of("user.dot", 200, createUserPojo(9909L, "user.dot", "pass9")),
                Arguments.of("user_email@domain.com", 200, createUserPojo(9910L, "user_email@domain.com", "pass10")),

                // 11-15: Numeric, extreme long username, empty username, null password
                Arguments.of("123456", 200, createUserPojo(9911L, "123456", "pass11")),
                Arguments.of("veryLongUsernameThatExceedsStandardLengthsForDatabaseValidationAndComplianceTestingCheck", 200, createUserPojo(9912L, "veryLongUsernameThatExceedsStandardLengthsForDatabaseValidationAndComplianceTestingCheck", "pass12")),
                Arguments.of("", 200, createUserPojo(9913L, "", "pass13")),
                Arguments.of("NullPassUser", 200, createUserPojo(9914L, "NullPassUser", null)),
                Arguments.of("NullUser", 200, createUserPojo(9915L, null, "pass15")),

                // 16-20: Missing optional fields or status boundaries
                Arguments.of("UserNoEmail", 200, new User(9916L, "UserNoEmail", "First", "Last", null, "pass16", "123-456", 1)),
                Arguments.of("UserNoPhone", 200, new User(9917L, "UserNoPhone", "First", "Last", "email@mail.com", "pass17", null, 1)),
                Arguments.of("UserNegativeStatus", 200, new User(9918L, "UserNegativeStatus", "First", "Last", "email@mail.com", "pass18", "123", -1)),
                Arguments.of("UserMaxLongId", 200, new User(9223372036854775807L, "UserMaxLongId", "First", "Last", "e@m.com", "pass19", "123", 0)),
                Arguments.of("UserNoStatus", 200, new User(9920L, "UserNoStatus", "First", "Last", "email@mail.com", "pass20", "123", null))
        );
    }

    private static User createUserPojo(Long id, String username, String password) {
        return new User(id, username, "First", "Last", "test@example.com", password, "555-0199", 1);
    }
}

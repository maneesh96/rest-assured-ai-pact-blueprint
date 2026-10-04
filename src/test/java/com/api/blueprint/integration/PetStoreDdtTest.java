package com.api.blueprint.integration;

import com.api.blueprint.config.ApiConfig;
import com.api.blueprint.models.Category;
import com.api.blueprint.models.Order;
import com.api.blueprint.models.Pet;
import com.api.blueprint.models.User;
import com.api.blueprint.support.TestTags;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@Tag(TestTags.REGRESSION)
public class PetStoreDdtTest {

    // Cases tagged "known-defect" send input the OpenAPI spec forbids. They assert
    // the status the spec requires, fail against the live petstore today, and are
    // excluded from the default run (see KNOWN_DEFECTS.md, run with -Pknown-defects).

    private static final String NO_STATUS_PARAM = "null_placeholder";

    // --- Scenario Set 1: Pet Status Finder Validation ---
    @ParameterizedTest(name = "DDT-Status: Querying status ''{0}'' should return code {1}")
    @CsvSource({
            "available, 200",
            "pending, 200",
            "sold, 200",
            "'available,pending', 200",
            "'pending,sold', 200",
            "'available,sold', 200",
            "'available,pending,sold', 200"
    })
    @DisplayName("Verify Pet Find By Status Endpoint with Various Query Inputs")
    public void findPetsByStatus_Validation(String status, int expectedStatusCode) {
        findPetsByStatus(status)
        .then()
                .statusCode(expectedStatusCode)
                .body("$", is(notNullValue()));
    }

    @Tag(TestTags.KNOWN_DEFECT)
    @ParameterizedTest(name = "DDT-Status: Querying status ''{0}'' should be rejected with {1}")
    @CsvSource({
            "unknown, 400",
            "invalid_enum_val, 400",
            NO_STATUS_PARAM + ", 400"
    })
    @DisplayName("Verify Pet Find By Status rejects a missing or out-of-enum status")
    public void findPetsByStatus_RejectsInvalidStatus(String status, int expectedStatusCode) {
        findPetsByStatus(status)
        .then()
                .statusCode(expectedStatusCode);
    }

    private static Response findPetsByStatus(String status) {
        var request = given().spec(ApiConfig.getBaseRequestSpec());
        if (!NO_STATUS_PARAM.equals(status)) {
            request.queryParam("status", status);
        }
        return request.when().get("/pet/findByStatus");
    }

    // --- Scenario Set 2: Pet Creation Payload Validation ---
    @ParameterizedTest(name = "DDT-Pet: Creating pet ''{0}'' with status ''{1}'' should return status {2}")
    @MethodSource("providePetCreationData")
    @DisplayName("Verify Pet Creation with Diverse Attributes & Boundaries")
    public void createPet_PayloadValidation(String name, String status, int expectedStatusCode, Pet petPayload) {
        createPet(petPayload, expectedStatusCode);
    }

    @Tag(TestTags.KNOWN_DEFECT)
    @ParameterizedTest(name = "DDT-Pet: Creating pet ''{0}'' without a required field should return status {2}")
    @MethodSource("provideInvalidPetCreationData")
    @DisplayName("Verify Pet Creation rejects payloads missing required fields")
    public void createPet_RejectsMissingRequiredField(String name, String status, int expectedStatusCode, Pet petPayload) {
        createPet(petPayload, expectedStatusCode);
    }

    private static void createPet(Pet petPayload, int expectedStatusCode) {
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
                // Happy paths with different statuses
                Arguments.of("Max", "available", 200, createPetPojo(99001L, "Max", "available")),
                Arguments.of("Bella", "pending", 200, createPetPojo(99002L, "Bella", "pending")),
                Arguments.of("Rocky", "sold", 200, createPetPojo(99003L, "Rocky", "sold")),
                Arguments.of("Luna", "available", 200, createPetPojo(99004L, "Luna", "available")),
                Arguments.of("Charlie", "pending", 200, createPetPojo(99005L, "Charlie", "pending")),

                // Special characters in name
                Arguments.of("Max@123", "available", 200, createPetPojo(99006L, "Max@123", "available")),
                Arguments.of("A&B", "available", 200, createPetPojo(99007L, "A&B", "available")),
                Arguments.of("Sparky-Doo", "available", 200, createPetPojo(99008L, "Sparky-Doo", "available")),
                Arguments.of("Shadow_1", "available", 200, createPetPojo(99009L, "Shadow_1", "available")),
                Arguments.of("O'Connor", "available", 200, createPetPojo(99010L, "O'Connor", "available")),

                // Numeric name / long name / empty name
                Arguments.of("12345", "available", 200, createPetPojo(99011L, "12345", "available")),
                Arguments.of("VeryLongPetNameThatExceedsStandardExpectationsButShouldBeHandledGracefullyByTheDatabaseSystemWithoutTruncation", "available", 200, createPetPojo(99012L, "VeryLongPetNameThatExceedsStandardExpectationsButShouldBeHandledGracefullyByTheDatabaseSystemWithoutTruncation", "available")),
                Arguments.of("", "available", 200, createPetPojo(99013L, "", "available")),
                Arguments.of("StatusNullPet", "available", 200, createPetPojo(99015L, "StatusNullPet", null)),

                // Missing elements / Custom categories
                Arguments.of("MaxNoUrls", "available", 200, new Pet(99016L, new Category(1, "Dogs"), "MaxNoUrls", new String[]{}, Collections.emptyList(), "available")),
                Arguments.of("NegativeIdPet", "available", 200, createPetPojo(-12345L, "NegativeIdPet", "available")),
                Arguments.of("MaxNoCategory", "available", 200, new Pet(99019L, null, "MaxNoCategory", new String[]{"http://img.url"}, Collections.emptyList(), "available")),
                Arguments.of("PetWithEmojiName🐶", "available", 200, createPetPojo(99020L, "PetWithEmojiName🐶", "available"))
        );
    }

    // "name" and "photoUrls" are required by the Pet schema.
    private static Stream<Arguments> provideInvalidPetCreationData() {
        return Stream.of(
                Arguments.of("NullNamePet", "available", 400, createPetPojo(99014L, null, "available")),
                Arguments.of("MaxNullUrls", "available", 400, new Pet(99017L, new Category(1, "Dogs"), "MaxNullUrls", null, Collections.emptyList(), "available"))
        );
    }

    private static Pet createPetPojo(Long id, String name, String status) {
        return new Pet(id, new Category(1, "Dogs"), name, new String[]{"http://images.com/pet.jpg"}, Collections.emptyList(), status);
    }

    // --- Scenario Set 3: Store Order Payload Validation ---
    @ParameterizedTest(name = "DDT-Order: Placing order petId={0}, quantity={1}, status={2} should return status {3}")
    @MethodSource("provideOrderCreationData")
    @DisplayName("Verify Order Creation Validation and Boundaries")
    public void storeOrder_Validation(Long petId, int quantity, String status, int expectedStatusCode, Order orderPayload) {
        placeOrder(orderPayload, expectedStatusCode);
    }

    @Tag(TestTags.KNOWN_DEFECT)
    @ParameterizedTest(name = "DDT-Order: Placing invalid order petId={0}, quantity={1}, status={2} should return status {3}")
    @MethodSource("provideInvalidOrderCreationData")
    @DisplayName("Verify Order Creation rejects invalid quantity, status and shipDate")
    public void storeOrder_RejectsInvalidOrder(Long petId, int quantity, String status, int expectedStatusCode, Order orderPayload) {
        placeOrder(orderPayload, expectedStatusCode);
    }

    private static void placeOrder(Order orderPayload, int expectedStatusCode) {
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
                // Valid orders with quantities
                Arguments.of(1L, 1, "placed", 200, createOrderPojo(9901L, 1L, 1, "placed", true)),
                Arguments.of(2L, 5, "placed", 200, createOrderPojo(9902L, 2L, 5, "placed", false)),
                Arguments.of(3L, 99, "approved", 200, createOrderPojo(9903L, 3L, 99, "approved", true)),
                Arguments.of(4L, 1000, "delivered", 200, createOrderPojo(9904L, 4L, 1000, "delivered", false)),
                Arguments.of(5L, 2, "placed", 200, createOrderPojo(9905L, 5L, 2, "placed", true)),

                // Boundary quantities
                Arguments.of(6L, 0, "placed", 200, createOrderPojo(9906L, 6L, 0, "placed", true)),
                Arguments.of(9L, 999999, "placed", 200, createOrderPojo(9909L, 9L, 999999, "placed", true)),

                // Missing or extreme fields
                Arguments.of(null, 1, "placed", 200, createOrderPojo(9911L, null, 1, "placed", true)),
                Arguments.of(12L, 1, null, 200, createOrderPojo(9912L, 12L, 1, null, true)),
                Arguments.of(13L, 1, "placed", 200, new Order(9913L, 13L, 1, null, "placed", null)),
                Arguments.of(14L, 1, "placed", 200, createOrderPojo(-9914L, 14L, 1, "placed", true)),
                Arguments.of(-1L, 1, "placed", 200, createOrderPojo(9915L, -1L, 1, "placed", true)),

                // Different date/time schemas and format boundaries
                Arguments.of(16L, 1, "placed", 200, new Order(9916L, 16L, 1, "2026-07-15T19:43:58.000Z", "placed", true)),
                Arguments.of(20L, 1, "placed", 200, new Order(9920L, 20L, 1, "2999-12-31T23:59:59.999+00:00", "approved", false))
        );
    }

    // A negative quantity is meaningless for an order; status is an enum; shipDate is
    // an RFC 3339 date-time. A malformed date must be a client error (400), not a 500.
    private static Stream<Arguments> provideInvalidOrderCreationData() {
        return Stream.of(
                Arguments.of(7L, -1, "placed", 400, createOrderPojo(9907L, 7L, -1, "placed", true)),
                Arguments.of(8L, -100, "placed", 400, createOrderPojo(9908L, 8L, -100, "placed", false)),
                Arguments.of(10L, 2, "unknown", 400, createOrderPojo(9910L, 10L, 2, "unknown", false)),
                Arguments.of(17L, 1, "placed", 400, new Order(9917L, 17L, 1, "2026-07-15", "placed", true)),
                Arguments.of(18L, 1, "placed", 400, new Order(9918L, 18L, 1, "invalid-date-format", "placed", true)),
                Arguments.of(19L, 1, "placed", 400, new Order(9919L, 19L, 1, "", "placed", true))
        );
    }

    private static Order createOrderPojo(Long id, Long petId, int quantity, String status, boolean complete) {
        return new Order(id, petId, quantity, "2026-07-15T14:14:00.000Z", status, complete);
    }

    // --- Scenario Set 4: User Creation Payload Validation ---
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
                // Valid User creation happy paths
                Arguments.of("user_1", 200, createUserPojo(9901L, "user_1", "pass1")),
                Arguments.of("user_2", 200, createUserPojo(9902L, "user_2", "pass2")),
                Arguments.of("user_3", 200, createUserPojo(9903L, "user_3", "pass3")),
                Arguments.of("user_4", 200, createUserPojo(9904L, "user_4", "pass4")),
                Arguments.of("user_5", 200, createUserPojo(9905L, "user_5", "pass5")),

                // Edge case usernames
                Arguments.of("u", 200, createUserPojo(9906L, "u", "pass6")),
                Arguments.of("user-with-hyphens", 200, createUserPojo(9907L, "user-with-hyphens", "pass7")),
                Arguments.of("user_with_underscores", 200, createUserPojo(9908L, "user_with_underscores", "pass8")),
                Arguments.of("user.dot", 200, createUserPojo(9909L, "user.dot", "pass9")),
                Arguments.of("user_email@domain.com", 200, createUserPojo(9910L, "user_email@domain.com", "pass10")),

                // Numeric, extreme long username, empty username, null password
                Arguments.of("123456", 200, createUserPojo(9911L, "123456", "pass11")),
                Arguments.of("veryLongUsernameThatExceedsStandardLengthsForDatabaseValidationAndComplianceTestingCheck", 200, createUserPojo(9912L, "veryLongUsernameThatExceedsStandardLengthsForDatabaseValidationAndComplianceTestingCheck", "pass12")),
                Arguments.of("", 200, createUserPojo(9913L, "", "pass13")),
                Arguments.of("NullPassUser", 200, createUserPojo(9914L, "NullPassUser", null)),
                Arguments.of("NullUser", 200, createUserPojo(9915L, null, "pass15")),

                // Missing optional fields or status boundaries
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

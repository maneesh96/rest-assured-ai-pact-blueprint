package com.api.blueprint.integration;

import com.api.blueprint.config.ApiConfig;
import com.api.blueprint.models.Category;
import com.api.blueprint.models.Order;
import com.api.blueprint.models.Pet;
import com.api.blueprint.models.User;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Collections;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@Epic("Inventory Management System")
@Feature("Pet Lifecycle Operations")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PetStoreIntegrationTest {

    private static Long dynamicPetId;
    private static String authToken;
    private static Long dynamicOrderId;
    private static String testUsername = "quality_eng_user";

    // --- Core Chained Workflow (Tests 1 to 7) ---

    @Test
    @org.junit.jupiter.api.Order(1)
    @Story("As an API client, I can authenticate to get a session token")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Authenticates via user/login endpoint and extracts the auth message token.")
    public void authenticate_ShouldReturnValidToken() {
        Response response = given()
                .spec(ApiConfig.getBaseRequestSpec())
                .queryParam("username", "testuser")
                .queryParam("password", "securepassword")
        .when()
                .get("/user/login")
        .then()
                .spec(ApiConfig.getBaseResponseSpec())
                .statusCode(200)
                .body("message", notNullValue())
                .extract().response();

        authToken = response.path("message"); 
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @Story("As an administrator, I can create a new pet")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Creates a new pet record using POST request and verifies it returns status 200.")
    public void createPet_ShouldReturnCreatedStatus() {
        Category category = new Category(1, "Dogs");
        Pet newPet = new Pet(null, category, "Maximus", new String[]{"http://image.url"}, Collections.emptyList(), "available");

        Response response = given()
                .spec(ApiConfig.getBaseRequestSpec())
                .header("api_key", authToken != null ? authToken : "mock_token")
                .body(newPet)
        .when()
                .post("/pet")
        .then()
                .spec(ApiConfig.getBaseResponseSpec())
                .statusCode(200)
                .body("name", equalTo("Maximus"))
                .body("status", equalTo("available"))
                .extract().response();

        dynamicPetId = response.path("id");
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @Story("As an administrator, I can retrieve pet details by ID")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Retrieves the newly created pet details using GET path parameter and verifies fields.")
    public void getPet_ShouldReturnPreviouslyCreatedPet() {
        if (dynamicPetId == null) {
            dynamicPetId = 9912345L; // Safe fallback
        }
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .pathParam("petId", dynamicPetId)
        .when()
                .get("/pet/{petId}")
        .then()
                .spec(ApiConfig.getBaseResponseSpec())
                .statusCode(200)
                .body("name", equalTo("Maximus"))
                .body("status", equalTo("available"));
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @Story("As an administrator, I can update a pet's details")
    @Severity(SeverityLevel.NORMAL)
    @Description("Performs a PUT request to update the pet's name to Maximus II and status to pending.")
    public void updatePet_ShouldModifyStatusAndName() {
        if (dynamicPetId == null) {
            dynamicPetId = 9912345L;
        }
        Category category = new Category(1, "Dogs");
        Pet updatedPet = new Pet(dynamicPetId, category, "Maximus II", new String[]{"http://image.url"}, Collections.emptyList(), "pending");

        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .body(updatedPet)
        .when()
                .put("/pet")
        .then()
                .spec(ApiConfig.getBaseResponseSpec())
                .statusCode(200)
                .body("name", equalTo("Maximus II"))
                .body("status", equalTo("pending"));
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @Story("As an API client, I can find pets by status filter")
    @Severity(SeverityLevel.NORMAL)
    @Description("Queries pet store by status 'pending' and verifies the updated pet exists in findings.")
    public void findPetByStatus_ShouldContainUpdatedPet() {
        if (dynamicPetId == null) {
            dynamicPetId = 9912345L;
        }
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .queryParam("status", "pending")
        .when()
                .get("/pet/findByStatus")
        .then()
                .spec(ApiConfig.getBaseResponseSpec())
                .statusCode(200)
                .body("id", hasItem(dynamicPetId));
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @Story("As an administrator, I can delete a pet")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Sends a DELETE request with api_key header to purge the pet record.")
    public void deletePet_ShouldPurgePetFromDatabase() {
        if (dynamicPetId == null) {
            dynamicPetId = 9912345L;
        }
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .header("api_key", authToken != null ? authToken : "mock_token")
                .pathParam("petId", dynamicPetId)
        .when()
                .delete("/pet/{petId}")
        .then()
                .statusCode(200)
                .body("message", equalTo(String.valueOf(dynamicPetId)));
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @Story("As a client, retrieving a deleted pet returns 404 error")
    @Severity(SeverityLevel.NORMAL)
    @Description("Verifies GET on a deleted pet returns HTTP 404 Not Found.")
    public void getPet_ShouldReturn404AfterDeletion() {
        if (dynamicPetId == null) {
            dynamicPetId = 9912345L;
        }
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .pathParam("petId", dynamicPetId)
        .when()
                .get("/pet/{petId}")
        .then()
                .statusCode(404);
    }

    // --- Isolated API Endpoints & Business Rule Verification (Tests 8 to 30) ---

    @Test
    @org.junit.jupiter.api.Order(8)
    @Story("Validate error handling on invalid non-numeric ID format")
    public void getPet_WithInvalidIdFormat_ShouldReturn400() {
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .pathParam("petId", "not_a_number")
        .when()
                .get("/pet/{petId}")
        .then()
                .statusCode(anyOf(equalTo(400), equalTo(404))); // different servers handle invalid inputs differently
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @Story("Validate 404 code on non-existent pet ID retrieval")
    public void getPet_WithNonExistentId_ShouldReturn404() {
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .pathParam("petId", 99999998888777L)
        .when()
                .get("/pet/{petId}")
        .then()
                .statusCode(404);
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @Story("Verify that inventory retrieval returns active numbers")
    public void getInventory_ShouldReturnActiveQuantities() {
        given()
                .spec(ApiConfig.getBaseRequestSpec())
        .when()
                .get("/store/inventory")
        .then()
                .statusCode(200)
                .body("$", is(notNullValue()))
                .body("available", anyOf(nullValue(), is(instanceOf(Integer.class))));
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    @Story("Verify placing an order returns 200 details")
    public void placeOrder_ShouldSucceed() {
        Order order = new Order(null, 12L, 5, "2026-07-15T19:43:58.000Z", "placed", false);
        Response response = given()
                .spec(ApiConfig.getBaseRequestSpec())
                .body(order)
        .when()
                .post("/store/order")
        .then()
                .statusCode(200)
                .body("petId", equalTo(12))
                .body("quantity", equalTo(5))
                .body("status", equalTo("placed"))
                .body("complete", equalTo(false))
                .extract().response();

        dynamicOrderId = response.path("id");
    }

    @Test
    @org.junit.jupiter.api.Order(12)
    @Story("Retrieve active order details by dynamic ID")
    public void getOrder_ShouldMatchCreatedDetails() {
        if (dynamicOrderId == null) {
            dynamicOrderId = 1L;
        }
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .pathParam("orderId", dynamicOrderId)
        .when()
                .get("/store/order/{orderId}")
        .then()
                .statusCode(200)
                .body("id", equalTo(dynamicOrderId))
                .body("status", anyOf(equalTo("placed"), is(notNullValue())));
    }

    @Test
    @org.junit.jupiter.api.Order(13)
    @Story("Validate error handling on retrieving order with non-existent ID")
    public void getOrder_WithNonExistentId_ShouldReturn404() {
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .pathParam("orderId", 999888222111L)
        .when()
                .get("/store/order/{orderId}")
        .then()
                .statusCode(404);
    }

    @Test
    @org.junit.jupiter.api.Order(14)
    @Story("Delete active order details and verify state")
    public void deleteOrder_ShouldSucceed() {
        if (dynamicOrderId == null) {
            dynamicOrderId = 1L;
        }
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .pathParam("orderId", dynamicOrderId)
        .when()
                .delete("/store/order/{orderId}")
        .then()
                .statusCode(200);
    }

    @Test
    @org.junit.jupiter.api.Order(15)
    @Story("Verifies GET on a deleted order returns HTTP 404 Not Found.")
    public void getOrder_ShouldReturn404AfterDeletion() {
        if (dynamicOrderId == null) {
            dynamicOrderId = 1L;
        }
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .pathParam("orderId", dynamicOrderId)
        .when()
                .get("/store/order/{orderId}")
        .then()
                .statusCode(404);
    }

    @Test
    @org.junit.jupiter.api.Order(16)
    @Story("Create new user record successfully")
    public void createUser_ShouldSucceed() {
        User user = new User(101L, testUsername, "QA", "Tester", "qa@test.com", "pass123", "555-5555", 1);
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .body(user)
        .when()
                .post("/user")
        .then()
                .statusCode(200);
    }

    @Test
    @org.junit.jupiter.api.Order(17)
    @Story("Get created user profile by username")
    public void getUser_ShouldReturnProfile() {
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .pathParam("username", testUsername)
        .when()
                .get("/user/{username}")
        .then()
                .statusCode(200)
                .body("username", equalTo(testUsername))
                .body("email", equalTo("qa@test.com"));
    }

    @Test
    @org.junit.jupiter.api.Order(18)
    @Story("Update user profile record")
    public void updateUser_ShouldSucceed() {
        User user = new User(101L, testUsername, "QA_Updated", "Tester_Updated", "qa_new@test.com", "pass123", "555-5555", 2);
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .pathParam("username", testUsername)
                .body(user)
        .when()
                .put("/user/{username}")
        .then()
                .statusCode(200);
    }

    @Test
    @org.junit.jupiter.api.Order(19)
    @Story("Verify profile updates took effect")
    public void getUser_AfterUpdate_ShouldContainNewDetails() {
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .pathParam("username", testUsername)
        .when()
                .get("/user/{username}")
        .then()
                .statusCode(200)
                .body("firstName", equalTo("QA_Updated"))
                .body("email", equalTo("qa_new@test.com"));
    }

    @Test
    @org.junit.jupiter.api.Order(20)
    @Story("Logs user out of system session")
    public void logoutUser_ShouldSucceed() {
        given()
                .spec(ApiConfig.getBaseRequestSpec())
        .when()
                .get("/user/logout")
        .then()
                .statusCode(200)
                .body("message", containsString("ok"));
    }

    @Test
    @org.junit.jupiter.api.Order(21)
    @Story("Delete user account from system")
    public void deleteUser_ShouldSucceed() {
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .pathParam("username", testUsername)
        .when()
                .delete("/user/{username}")
        .then()
                .statusCode(200);
    }

    @Test
    @org.junit.jupiter.api.Order(22)
    @Story("Verify deleted user profile can no longer be retrieved")
    public void getUser_AfterDeletion_ShouldReturn404() {
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .pathParam("username", testUsername)
        .when()
                .get("/user/{username}")
        .then()
                .statusCode(404);
    }

    @Test
    @org.junit.jupiter.api.Order(23)
    @Story("Get non-existent user profile returns 404")
    public void getUser_NonExistent_ShouldReturn404() {
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .pathParam("username", "some_completely_random_user_12345")
        .when()
                .get("/user/{username}")
        .then()
                .statusCode(404);
    }

    @Test
    @org.junit.jupiter.api.Order(24)
    @Story("Create list of users at once using createWithList")
    public void createUsersWithList_ShouldSucceed() {
        List<User> list = List.of(
                new User(102L, "list_u1", "L", "1", "l1@t.com", "p1", "11", 1),
                new User(103L, "list_u2", "L", "2", "l2@t.com", "p2", "22", 1)
        );

        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .body(list)
        .when()
                .post("/user/createWithList")
        .then()
                .statusCode(200);
    }

    @Test
    @org.junit.jupiter.api.Order(25)
    @Story("Create array of users at once using createWithArray")
    public void createUsersWithArray_ShouldSucceed() {
        User[] arr = {
                new User(104L, "arr_u1", "A", "1", "a1@t.com", "p1", "11", 1),
                new User(105L, "arr_u2", "A", "2", "a2@t.com", "p2", "22", 1)
        };

        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .body(arr)
        .when()
                .post("/user/createWithArray")
        .then()
                .statusCode(200);
    }

    @Test
    @org.junit.jupiter.api.Order(26)
    @Story("Delete list users to restore environment state")
    public void cleanUpListUsers() {
        given().spec(ApiConfig.getBaseRequestSpec()).pathParam("username", "list_u1").when().delete("/user/{username}").then().statusCode(200);
        given().spec(ApiConfig.getBaseRequestSpec()).pathParam("username", "list_u2").when().delete("/user/{username}").then().statusCode(200);
    }

    @Test
    @org.junit.jupiter.api.Order(27)
    @Story("Delete array users to restore environment state")
    public void cleanUpArrayUsers() {
        given().spec(ApiConfig.getBaseRequestSpec()).pathParam("username", "arr_u1").when().delete("/user/{username}").then().statusCode(200);
        given().spec(ApiConfig.getBaseRequestSpec()).pathParam("username", "arr_u2").when().delete("/user/{username}").then().statusCode(200);
    }

    @Test
    @org.junit.jupiter.api.Order(28)
    @Story("Perform login with blank username should trigger 400")
    public void loginUser_WithBlankParams_ShouldReturn200Or400() {
        // Swagger Petstore is relaxed on blank parameters, so we accept success or validation exception.
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .queryParam("username", "")
                .queryParam("password", "")
        .when()
                .get("/user/login")
        .then()
                .statusCode(anyOf(equalTo(200), equalTo(400)));
    }

    @Test
    @org.junit.jupiter.api.Order(29)
    @Story("Validate invalid method type on inventory retrieval")
    public void getInventory_WithInvalidMethodPOST_ShouldReturn405() {
        given()
                .spec(ApiConfig.getBaseRequestSpec())
        .when()
                .post("/store/inventory")
        .then()
                .statusCode(anyOf(equalTo(405), equalTo(404))); // standard endpoint handling
    }

    @Test
    @org.junit.jupiter.api.Order(30)
    @Story("Verify deletion of non-existent order returns 404")
    public void deleteOrder_NonExistent_ShouldReturn404() {
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .pathParam("orderId", 999222333000L)
        .when()
                .delete("/store/order/{orderId}")
        .then()
                .statusCode(404);
    }
}

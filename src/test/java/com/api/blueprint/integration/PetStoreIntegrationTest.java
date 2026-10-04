package com.api.blueprint.integration;

import com.api.blueprint.config.ApiConfig;
import com.api.blueprint.integration.support.PetStoreFixture;
import com.api.blueprint.models.Order;
import com.api.blueprint.models.Pet;
import com.api.blueprint.models.User;
import com.api.blueprint.support.TestTags;
import io.qameta.allure.*;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests against the Swagger Petstore.
 *
 * <p>Each test builds the data it needs through {@link PetStoreFixture} and the fixture removes
 * it afterwards, so any test can run alone, in any order, and a failure points at the
 * operation that broke rather than at an earlier test in a chain.
 */
@Epic("Inventory Management System")
@Feature("Pet Lifecycle Operations")
@Tag(TestTags.REGRESSION)
public class PetStoreIntegrationTest {

    private final PetStoreFixture fixture = new PetStoreFixture();

    @AfterEach
    void removeTestData() {
        fixture.cleanUp();
    }

    // --- Pet lifecycle ---

    @Test
    @Tag(TestTags.SMOKE)
    @Story("As an API client, I can authenticate to get a session token")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Authenticates via user/login and checks that a session token message is returned.")
    public void authenticate_ShouldReturnValidToken() {
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .queryParam("username", PetStoreFixture.uniqueUsername("login"))
                .queryParam("password", "securepassword")
        .when()
                .get("/user/login")
        .then()
                .spec(ApiConfig.getBaseResponseSpec())
                .statusCode(200)
                .body("message", containsString("logged in user session"));
    }

    @Test
    @Tag(TestTags.SMOKE)
    @Story("As an administrator, I can create a new pet")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Creates a pet with POST and verifies the stored record echoes the request.")
    public void createPet_ShouldReturnCreatedPet() {
        Pet pet = fixture.newPet("Maximus", "available");
        fixture.trackPet(pet.getId());

        JsonPath created = given()
                .spec(ApiConfig.getBaseRequestSpec())
                .body(pet)
        .when()
                .post("/pet")
        .then()
                .spec(ApiConfig.getBaseResponseSpec())
                .statusCode(200)
                .body("name", equalTo("Maximus"))
                .body("status", equalTo("available"))
                .extract().jsonPath();

        assertThat(created.getLong("id"), equalTo(pet.getId()));
    }

    @Test
    @Tag(TestTags.SMOKE)
    @Story("As an administrator, I can retrieve pet details by ID")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Retrieves a freshly created pet by id and verifies its fields.")
    public void getPet_ShouldReturnExistingPet() {
        Pet pet = fixture.givenPet("Maximus", "available");

        JsonPath fetched = fixture.getWhenStatus(200, "/pet/{petId}", pet.getId())
        .then()
                .spec(ApiConfig.getBaseResponseSpec())
                .statusCode(200)
                .body("name", equalTo("Maximus"))
                .body("status", equalTo("available"))
                .extract().jsonPath();

        assertThat(fetched.getLong("id"), equalTo(pet.getId()));
    }

    @Test
    @Story("As an administrator, I can update a pet's details")
    @Severity(SeverityLevel.NORMAL)
    @Description("Updates a pet's name and status with PUT and verifies the response.")
    public void updatePet_ShouldModifyStatusAndName() {
        Pet pet = fixture.givenPet("Maximus", "available");
        pet.setName("Maximus II");
        pet.setStatus("pending");

        JsonPath updated = given()
                .spec(ApiConfig.getBaseRequestSpec())
                .body(pet)
        .when()
                .put("/pet")
        .then()
                .spec(ApiConfig.getBaseResponseSpec())
                .statusCode(200)
                .body("name", equalTo("Maximus II"))
                .body("status", equalTo("pending"))
                .extract().jsonPath();

        assertThat(updated.getLong("id"), equalTo(pet.getId()));
    }

    @Test
    @Story("As an API client, I can find pets by status filter")
    @Severity(SeverityLevel.NORMAL)
    @Description("Creates a pending pet and verifies findByStatus=pending lists it.")
    public void findPetByStatus_ShouldContainPendingPet() {
        Pet pet = fixture.givenPet("Maximus", "pending");

        List<Long> pendingIds = given()
                .spec(ApiConfig.getBaseRequestSpec())
                .queryParam("status", "pending")
        .when()
                .get("/pet/findByStatus")
        .then()
                .spec(ApiConfig.getBaseResponseSpec())
                .statusCode(200)
                .extract().jsonPath().getList("id", Long.class);

        assertThat(pendingIds, hasItem(pet.getId()));
    }

    @Test
    @Story("As an administrator, I can delete a pet")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Deletes a pet with the api_key header and verifies the deleted id is echoed.")
    public void deletePet_ShouldPurgePetFromDatabase() {
        Pet pet = fixture.givenPet("Maximus", "available");

        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .header("api_key", PetStoreFixture.API_KEY)
                .pathParam("petId", pet.getId())
        .when()
                .delete("/pet/{petId}")
        .then()
                .statusCode(200)
                .body("message", equalTo(String.valueOf(pet.getId())));
    }

    @Test
    @Story("As a client, retrieving a deleted pet returns 404 error")
    @Severity(SeverityLevel.NORMAL)
    @Description("Deletes a pet and verifies a subsequent GET returns HTTP 404 Not Found.")
    public void getPet_ShouldReturn404AfterDeletion() {
        Pet pet = fixture.givenPet("Maximus", "available");
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .header("api_key", PetStoreFixture.API_KEY)
        .when()
                .delete("/pet/{petId}", pet.getId())
        .then()
                .statusCode(200);

        fixture.getWhenStatus(404, "/pet/{petId}", pet.getId())
        .then()
                .statusCode(404);
    }

    // --- Pet error handling and inventory ---

    @Test
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
    @Story("Validate 404 code on non-existent pet ID retrieval")
    public void getPet_WithNonExistentId_ShouldReturn404() {
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .pathParam("petId", PetStoreFixture.uniqueId())
        .when()
                .get("/pet/{petId}")
        .then()
                .statusCode(404);
    }

    @Test
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

    // --- Store orders ---

    @Test
    @Tag(TestTags.SMOKE)
    @Story("Verify placing an order returns 200 details")
    public void placeOrder_ShouldSucceed() {
        Order order = fixture.newOrder();
        fixture.trackOrder(order.getId());

        JsonPath placed = given()
                .spec(ApiConfig.getBaseRequestSpec())
                .body(order)
        .when()
                .post("/store/order")
        .then()
                .statusCode(200)
                .body("quantity", equalTo(5))
                .body("status", equalTo("placed"))
                .body("complete", equalTo(false))
                .extract().jsonPath();

        assertThat(placed.getLong("id"), equalTo(order.getId()));
        assertThat(placed.getLong("petId"), equalTo(order.getPetId()));
    }

    @Test
    @Story("Retrieve active order details by ID")
    public void getOrder_ShouldMatchCreatedDetails() {
        Order order = fixture.givenOrder();

        JsonPath fetched = fixture.getWhenStatus(200, "/store/order/{orderId}", order.getId())
        .then()
                .statusCode(200)
                .body("status", equalTo("placed"))
                .extract().jsonPath();

        assertThat(fetched.getLong("id"), equalTo(order.getId()));
        assertThat(fetched.getLong("petId"), equalTo(order.getPetId()));
    }

    @Test
    @Story("Validate error handling on retrieving order with non-existent ID")
    public void getOrder_WithNonExistentId_ShouldReturn404() {
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .pathParam("orderId", PetStoreFixture.uniqueId())
        .when()
                .get("/store/order/{orderId}")
        .then()
                .statusCode(404);
    }

    @Test
    @Story("Delete an order")
    public void deleteOrder_ShouldSucceed() {
        Order order = fixture.givenOrder();

        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .pathParam("orderId", order.getId())
        .when()
                .delete("/store/order/{orderId}")
        .then()
                .statusCode(200);
    }

    @Test
    @Story("Verifies GET on a deleted order returns HTTP 404 Not Found.")
    public void getOrder_ShouldReturn404AfterDeletion() {
        Order order = fixture.givenOrder();
        given()
                .spec(ApiConfig.getBaseRequestSpec())
        .when()
                .delete("/store/order/{orderId}", order.getId())
        .then()
                .statusCode(200);

        fixture.getWhenStatus(404, "/store/order/{orderId}", order.getId())
        .then()
                .statusCode(404);
    }

    @Test
    @Story("Verify deletion of non-existent order returns 404")
    public void deleteOrder_NonExistent_ShouldReturn404() {
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .pathParam("orderId", PetStoreFixture.uniqueId())
        .when()
                .delete("/store/order/{orderId}")
        .then()
                .statusCode(404);
    }

    // --- Users ---

    @Test
    @Tag(TestTags.SMOKE)
    @Story("Create new user record successfully")
    public void createUser_ShouldSucceed() {
        User user = fixture.newUser();
        fixture.trackUser(user.getUsername());

        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .body(user)
        .when()
                .post("/user")
        .then()
                .statusCode(200);
    }

    @Test
    @Story("Get created user profile by username")
    public void getUser_ShouldReturnProfile() {
        User user = fixture.givenUser();

        fixture.getWhenStatus(200, "/user/{username}", user.getUsername())
        .then()
                .statusCode(200)
                .body("username", equalTo(user.getUsername()))
                .body("email", equalTo(user.getEmail()));
    }

    @Test
    @Story("Update user profile record")
    public void updateUser_ShouldSucceed() {
        User user = fixture.givenUser();
        user.setFirstName("QA_Updated");

        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .pathParam("username", user.getUsername())
                .body(user)
        .when()
                .put("/user/{username}")
        .then()
                .statusCode(200);
    }

    @Test
    @Story("Verify profile updates took effect")
    public void getUser_AfterUpdate_ShouldContainNewDetails() {
        User user = fixture.givenUser();
        user.setFirstName("QA_Updated");
        user.setEmail("updated_" + user.getEmail());
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .body(user)
        .when()
                .put("/user/{username}", user.getUsername())
        .then()
                .statusCode(200);

        fixture.getWhenStatus(200, "/user/{username}", user.getUsername())
        .then()
                .statusCode(200)
                .body("firstName", equalTo("QA_Updated"))
                .body("email", equalTo(user.getEmail()));
    }

    @Test
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
    @Story("Delete user account from system")
    public void deleteUser_ShouldSucceed() {
        User user = fixture.givenUser();

        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .pathParam("username", user.getUsername())
        .when()
                .delete("/user/{username}")
        .then()
                .statusCode(200);
    }

    @Test
    @Story("Verify deleted user profile can no longer be retrieved")
    public void getUser_AfterDeletion_ShouldReturn404() {
        User user = fixture.givenUser();
        given()
                .spec(ApiConfig.getBaseRequestSpec())
        .when()
                .delete("/user/{username}", user.getUsername())
        .then()
                .statusCode(200);

        fixture.getWhenStatus(404, "/user/{username}", user.getUsername())
        .then()
                .statusCode(404);
    }

    @Test
    @Story("Get non-existent user profile returns 404")
    public void getUser_NonExistent_ShouldReturn404() {
        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .pathParam("username", PetStoreFixture.uniqueUsername("missing"))
        .when()
                .get("/user/{username}")
        .then()
                .statusCode(404);
    }

    @Test
    @Story("Create list of users at once using createWithList")
    public void createUsersWithList_ShouldCreateEveryUser() {
        List<User> users = List.of(fixture.newUser(), fixture.newUser());
        users.forEach(user -> fixture.trackUser(user.getUsername()));

        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .body(users)
        .when()
                .post("/user/createWithList")
        .then()
                .statusCode(200);

        users.forEach(user -> fixture.getWhenStatus(200, "/user/{username}", user.getUsername())
                .then()
                .statusCode(200)
                .body("username", equalTo(user.getUsername())));
    }

    @Test
    @Story("Create array of users at once using createWithArray")
    public void createUsersWithArray_ShouldCreateEveryUser() {
        User[] users = {fixture.newUser(), fixture.newUser()};
        for (User user : users) {
            fixture.trackUser(user.getUsername());
        }

        given()
                .spec(ApiConfig.getBaseRequestSpec())
                .body(users)
        .when()
                .post("/user/createWithArray")
        .then()
                .statusCode(200);

        for (User user : users) {
            fixture.getWhenStatus(200, "/user/{username}", user.getUsername())
            .then()
                    .statusCode(200)
                    .body("username", equalTo(user.getUsername()));
        }
    }

    @Test
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
    @Story("Validate invalid method type on inventory retrieval")
    public void getInventory_WithInvalidMethodPOST_ShouldReturn405() {
        given()
                .spec(ApiConfig.getBaseRequestSpec())
        .when()
                .post("/store/inventory")
        .then()
                .statusCode(anyOf(equalTo(405), equalTo(404))); // standard endpoint handling
    }
}

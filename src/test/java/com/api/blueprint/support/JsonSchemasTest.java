package com.api.blueprint.support;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

/**
 * Offline checks that the response schemas accept what the Petstore really returns and
 * reject what the OpenAPI spec forbids, so a schema that is too loose (or too strict) is
 * caught here rather than by a confusing failure in the live suite.
 */
@Tag(TestTags.SMOKE)
@Tag(TestTags.REGRESSION)
class JsonSchemasTest {

    @Nested
    class PetSchema {

        private static final String VALID_PET = """
                {"id": 9223372036854775807, "category": {"id": 1, "name": "Dogs"}, "name": "Maximus",
                 "photoUrls": ["https://example.com/Maximus.png"], "tags": [], "status": "available"}""";

        @Test
        void acceptsPetAsReturnedByPetstore() {
            assertThat(VALID_PET, matchesJsonSchemaInClasspath(JsonSchemas.PET));
        }

        @ParameterizedTest(name = "rejects {0}")
        @ValueSource(strings = {
                "{\"id\": 1, \"photoUrls\": [], \"status\": \"available\"}",
                "{\"id\": 1, \"name\": \"Rex\", \"photoUrls\": \"not-an-array\", \"status\": \"available\"}",
                "{\"id\": 1, \"name\": \"Rex\", \"photoUrls\": [], \"status\": \"lost\"}",
                "{\"id\": \"1\", \"name\": \"Rex\", \"photoUrls\": [], \"status\": \"sold\"}"
        })
        void rejectsPetThatBreaksTheSpec(String body) {
            assertThat(body, not(matchesJsonSchemaInClasspath(JsonSchemas.PET)));
        }
    }

    @Nested
    class OrderSchema {

        private static final String VALID_ORDER = """
                {"id": 12345678901, "petId": 98765432101, "quantity": 5,
                 "shipDate": "2026-07-15T19:43:58.000+0000", "status": "placed", "complete": false}""";

        @Test
        void acceptsOrderAsReturnedByPetstore() {
            assertThat(VALID_ORDER, matchesJsonSchemaInClasspath(JsonSchemas.ORDER));
        }

        @ParameterizedTest(name = "rejects {0}")
        @ValueSource(strings = {
                "{\"id\": 1, \"petId\": 2, \"quantity\": 5, \"shipDate\": \"2026-07-15T19:43:58.000+0000\", \"status\": \"shipped\", \"complete\": false}",
                "{\"id\": 1, \"petId\": 2, \"quantity\": 5, \"shipDate\": \"tomorrow\", \"status\": \"placed\", \"complete\": false}",
                "{\"id\": 1, \"petId\": 2, \"quantity\": 5, \"shipDate\": \"2026-07-15T19:43:58.000+0000\", \"status\": \"placed\", \"complete\": \"no\"}",
                "{\"id\": 1, \"quantity\": 5, \"shipDate\": \"2026-07-15T19:43:58.000+0000\", \"status\": \"placed\", \"complete\": false}"
        })
        void rejectsOrderThatBreaksTheSpec(String body) {
            assertThat(body, not(matchesJsonSchemaInClasspath(JsonSchemas.ORDER)));
        }
    }
}

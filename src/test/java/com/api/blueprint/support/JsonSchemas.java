package com.api.blueprint.support;

/**
 * Classpath locations of the JSON schemas that response bodies are validated against.
 * Each schema mirrors a component of {@code schemas/petstore-openapi.yaml}.
 */
public final class JsonSchemas {

    public static final String PET = "schemas/json/pet.json";
    public static final String ORDER = "schemas/json/order.json";

    private JsonSchemas() {
    }
}

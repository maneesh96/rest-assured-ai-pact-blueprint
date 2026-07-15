package com.api.blueprint.config;

import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import org.hamcrest.Matchers;

import java.io.InputStream;
import java.util.Properties;

public class ApiConfig {
    private static final Properties properties = new Properties();
    private static final String BASE_URL;

    static {
        try (InputStream input = ApiConfig.class.getClassLoader().getResourceAsStream("test-env.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (Exception e) {
            System.err.println("Could not load test-env.properties: " + e.getMessage());
        }

        // Allow system property or environment override
        String defaultUrl = properties.getProperty("api.base.url", "https://petstore.swagger.io/v2");
        String sysUrl = System.getProperty("api.base.url");
        String envUrl = System.getenv("API_BASE_URL");

        if (sysUrl != null && !sysUrl.isBlank()) {
            BASE_URL = sysUrl;
        } else if (envUrl != null && !envUrl.isBlank()) {
            BASE_URL = envUrl;
        } else {
            BASE_URL = defaultUrl;
        }
    }

    public static String getBaseUrl() {
        return BASE_URL;
    }

    /**
     * Constructs the baseline request specification.
     * Injects the AllureRestAssured filter to guarantee all HTTP traffic is logged to the HTML report.
     */
    public static RequestSpecification getBaseRequestSpec() {
        return new RequestSpecBuilder()
                .setBaseUri(BASE_URL)
                .setContentType(ContentType.JSON)
                .addFilter(new AllureRestAssured()) // Automatically attaches requests/responses to Allure
                .log(LogDetail.ALL)
                .build();
    }

    /**
     * Constructs the baseline response specification.
     * Enforces global latency SLA checks and strict Content-Type validation.
     */
    public static ResponseSpecification getBaseResponseSpec() {
        return new ResponseSpecBuilder()
                .expectContentType(ContentType.JSON)
                .expectResponseTime(Matchers.lessThan(5000L)) // Fails the test if latency exceeds 5 seconds
                .build();
    }
}

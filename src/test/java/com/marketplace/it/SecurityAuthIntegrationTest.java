package com.marketplace.it;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real end-to-end integration test for Authentication (TS1).
 * This test requires SAM Local API to be running (usually on port 3001).
 */
public class SecurityAuthIntegrationTest {

    private static String apiBaseUrl;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @BeforeAll
    public static void setup() {
        // Use environment variable or default to localhost:3001 (configured in run_all.sh)
        apiBaseUrl = System.getenv().getOrDefault("SAM_API_URL", "http://localhost:3001");
        if (apiBaseUrl.endsWith("/")) {
            apiBaseUrl = apiBaseUrl.substring(0, apiBaseUrl.length() - 1);
        }
    }

    /**
     * TS1: Verify that a request without an Authorization header is blocked.
     */
    @Test
    public void shouldReturn403WhenTokenIsMissing() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl + "/products"))
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Test\"}"))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // API Gateway with Lambda Authorizer returns 403 Forbidden for Deny or 401 Unauthorized for missing token
        assertThat(response.statusCode()).isIn(401, 403);
    }

    /**
     * TS1: Verify that an invalid token results in 403 Forbidden.
     */
    @Test
    public void shouldReturn403WhenTokenIsInvalid() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl + "/products"))
                .header("Authorization", "Bearer invalid-token")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Test\"}"))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(403);
    }

    /**
     * Verify that a valid token is accepted.
     */
    @Test
    public void shouldAllowRequestWithValidToken() throws Exception {
        // The LambdaAuthorizerHandler allows "Bearer allow-me"
        String productJson = "{\"name\": \"Auth Test Product\", \"price\": 10.0, \"category\": \"Test\", \"stockQuantity\": 5}";
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl + "/products"))
                .header("Authorization", "Bearer allow-me")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(productJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(201);
    }
}

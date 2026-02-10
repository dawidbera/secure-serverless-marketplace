package com.marketplace.products;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CreateProductHandler.
 */
@ExtendWith(MockitoExtension.class)
public class CreateProductHandlerTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private KmsClient kmsClient;

    @Mock
    private SecretsManagerClient secretsManagerClient;

    @Mock
    private Context context;

    @Mock
    private LambdaLogger logger;

    private CreateProductHandler handler;

    /**
     * Sets up the test environment before each test.
     */
    @BeforeEach
    public void setUp() {
        lenient().when(context.getLogger()).thenReturn(logger);
        handler = new CreateProductHandler(dynamoDbClient, kmsClient, secretsManagerClient, 
                "TestTable", "test-key-id", "test-secret-arn");
    }

    /**
     * Tests that a 400 error is returned when the product name is missing.
     */
    @Test
    public void shouldReturn400WhenNameIsMissing() {
        // Given
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withBody("{\"price\": 100.0, \"category\": \"Electronics\"}");

        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(GetSecretValueResponse.builder().name("LogisticsApiKey").build());

        // When
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getBody()).contains("Product name is required");
    }

    /**
     * Tests that a 400 error is returned when the price is less than or equal to zero.
     */
    @Test
    public void shouldReturn400WhenPriceIsInvalid() {
        // Given
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withBody("{\"name\": \"Test\", \"price\": -1.0, \"category\": \"Electronics\"}");

        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(GetSecretValueResponse.builder().name("LogisticsApiKey").build());

        // When
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getBody()).contains("Product price must be greater than zero");
    }

    /**
     * Tests successful product creation.
     */
    @Test
    public void shouldCreateProductSuccessfully() {
        // Given
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withBody("{\"name\": \"Test Product\", \"price\": 100.0, \"category\": \"Electronics\"}");

        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(GetSecretValueResponse.builder().name("LogisticsApiKey").build());

        // When
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(201);
        verify(dynamoDbClient, times(1)).putItem(any(PutItemRequest.class));
        assertThat(response.getBody()).contains("Test Product");
    }

    /**
     * Tests error handling when an exception occurs.
     */
    @Test
    public void shouldReturnErrorOnException() {
        // Given
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withBody("{\"name\": \"Test Product\"}");

        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenThrow(new RuntimeException("Secrets Manager error"));

        // When
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(500);
        assertThat(response.getBody()).contains("Could not create product");
        verify(logger).log(contains("Error creating product: Secrets Manager error"));
    }
}
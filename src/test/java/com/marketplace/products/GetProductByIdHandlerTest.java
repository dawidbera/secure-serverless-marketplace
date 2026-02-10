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
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.kms.KmsClient;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GetProductByIdHandler.
 */
@ExtendWith(MockitoExtension.class)
public class GetProductByIdHandlerTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private KmsClient kmsClient;

    @Mock
    private Context context;

    @Mock
    private LambdaLogger logger;

    private GetProductByIdHandler handler;

    /**
     * Sets up the test environment before each test.
     */
    @BeforeEach
    public void setUp() {
        lenient().when(context.getLogger()).thenReturn(logger);
        handler = new GetProductByIdHandler(dynamoDbClient, kmsClient, "TestTable");
    }

    /**
     * Tests successful product retrieval by ID.
     */
    @Test
    public void shouldReturnProductWhenExists() {
        // Given
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withRequestContext(new APIGatewayProxyRequestEvent.ProxyRequestContext())
                .withPathParameters(Map.of("id", "prod-1"));

        Map<String, AttributeValue> item = Map.of(
                "id", AttributeValue.builder().s("prod-1").build(),
                "name", AttributeValue.builder().s("Test Product").build(),
                "price", AttributeValue.builder().n("10.0").build(),
                "category", AttributeValue.builder().s("Test").build(),
                "stockQuantity", AttributeValue.builder().n("5").build(),
                "version", AttributeValue.builder().n("1").build()
        );

        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().item(item).build());

        // When
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("Test Product");
    }

    /**
     * Tests behavior when the product is not found.
     */
    @Test
    public void shouldReturn404WhenNotFound() {
        // Given
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withRequestContext(new APIGatewayProxyRequestEvent.ProxyRequestContext())
                .withPathParameters(Map.of("id", "non-existent"));

        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().item(null).build());

        // When
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(404);
        assertThat(response.getBody()).contains("Product not found");
    }
}
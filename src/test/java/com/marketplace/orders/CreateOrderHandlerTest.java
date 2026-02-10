package com.marketplace.orders;

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
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CreateOrderHandler.
 */
@ExtendWith(MockitoExtension.class)
public class CreateOrderHandlerTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private Context context;

    @Mock
    private LambdaLogger logger;

    private CreateOrderHandler handler;

    @BeforeEach
    public void setUp() {
        lenient().when(context.getLogger()).thenReturn(logger);
        handler = new CreateOrderHandler(dynamoDbClient, "TestTable");
    }

    /**
     * Tests successful order creation.
     */
    @Test
    public void shouldCreateOrderSuccessfully() {
        // Given
        APIGatewayProxyRequestEvent.ProxyRequestContext proxyContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        proxyContext.setAuthorizer(Map.of("user_id", "user-123"));
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withRequestContext(proxyContext)
                .withBody("{\"productId\": \"prod-1\", \"quantity\": 2}");

        Map<String, AttributeValue> productItem = Map.of(
                "stockQuantity", AttributeValue.builder().n("10").build(),
                "version", AttributeValue.builder().n("1").build()
        );

        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().item(productItem).build());

        // When
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(201);
        verify(dynamoDbClient, times(1)).transactWriteItems(any(TransactWriteItemsRequest.class));
    }

    /**
     * Tests error when product is not found.
     */
    @Test
    public void shouldReturn404WhenProductNotFound() {
        // Given
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withRequestContext(new APIGatewayProxyRequestEvent.ProxyRequestContext())
                .withBody("{\"productId\": \"non-existent\", \"quantity\": 1}");

        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().item(null).build());

        // When
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(404);
        assertThat(response.getBody()).contains("Product not found");
    }

    /**
     * Tests error when stock is insufficient.
     */
    @Test
    public void shouldReturn400WhenInsufficientStock() {
        // Given
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withRequestContext(new APIGatewayProxyRequestEvent.ProxyRequestContext())
                .withBody("{\"productId\": \"prod-1\", \"quantity\": 100}");

        Map<String, AttributeValue> productItem = Map.of(
                "stockQuantity", AttributeValue.builder().n("10").build(),
                "version", AttributeValue.builder().n("1").build()
        );

        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().item(productItem).build());

        // When
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getBody()).contains("Insufficient stock");
    }
}

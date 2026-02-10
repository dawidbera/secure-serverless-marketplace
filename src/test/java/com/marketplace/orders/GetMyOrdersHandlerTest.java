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
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GetMyOrdersHandler.
 */
@ExtendWith(MockitoExtension.class)
public class GetMyOrdersHandlerTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private Context context;

    @Mock
    private LambdaLogger logger;

    private GetMyOrdersHandler handler;

    @BeforeEach
    public void setUp() {
        lenient().when(context.getLogger()).thenReturn(logger);
        handler = new GetMyOrdersHandler(dynamoDbClient, "TestTable");
    }

    /**
     * Tests successful retrieval of user orders.
     */
    @Test
    public void shouldReturnOrdersSuccessfully() {
        // Given
        APIGatewayProxyRequestEvent.ProxyRequestContext proxyContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        proxyContext.setAuthorizer(Map.of("user_id", "user-123"));
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withRequestContext(proxyContext);

        Map<String, AttributeValue> orderItem = Map.of(
                "orderId", AttributeValue.builder().s("ord-1").build(),
                "productId", AttributeValue.builder().s("prod-1").build(),
                "userId", AttributeValue.builder().s("user-123").build(),
                "quantity", AttributeValue.builder().n("1").build(),
                "timestamp", AttributeValue.builder().n("123456789").build()
        );

        when(dynamoDbClient.query(any(QueryRequest.class)))
                .thenReturn(QueryResponse.builder().items(List.of(orderItem)).build());

        // When
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("ord-1");
    }

    /**
     * Tests behavior when no orders are found.
     */
    @Test
    public void shouldReturnEmptyListWhenNoOrdersExist() {
        // Given
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withRequestContext(new APIGatewayProxyRequestEvent.ProxyRequestContext());

        when(dynamoDbClient.query(any(QueryRequest.class)))
                .thenReturn(QueryResponse.builder().items(Collections.emptyList()).build());

        // When
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("[]");
    }
}
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
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.ssm.SsmClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GetProductsHandler.
 */
@ExtendWith(MockitoExtension.class)
public class GetProductsHandlerTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private KmsClient kmsClient;

    @Mock
    private SsmClient ssmClient;

    @Mock
    private Context context;

    @Mock
    private LambdaLogger logger;

    private GetProductsHandler handler;

    /**
     * Sets up the test environment before each test.
     */
    @BeforeEach
    public void setUp() {
        lenient().when(context.getLogger()).thenReturn(logger);
        handler = new GetProductsHandler(dynamoDbClient, kmsClient, ssmClient, "TestTable");
    }

    /**
     * Tests successful retrieval of all products.
     */
    @Test
    public void shouldReturnEmptyListWhenNoProductsExist() {
        // Given
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        
        when(dynamoDbClient.scan(any(ScanRequest.class)))
                .thenReturn(ScanResponse.builder().items(Collections.emptyList()).build());

        // When
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("[]");
    }

    /**
     * Tests successful retrieval of products when they exist.
     */
    @Test
    public void shouldReturnProductsListWhenProductsExist() {
        // Given
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        
        Map<String, AttributeValue> item = Map.of(
                "id", AttributeValue.builder().s("123").build(),
                "name", AttributeValue.builder().s("Test Product").build(),
                "price", AttributeValue.builder().n("10.0").build(),
                "category", AttributeValue.builder().s("Test").build()
        );

        when(dynamoDbClient.scan(any(ScanRequest.class)))
                .thenReturn(ScanResponse.builder().items(List.of(item)).build());

        // When
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("Test Product");
        assertThat(response.getBody()).contains("123");
    }
}

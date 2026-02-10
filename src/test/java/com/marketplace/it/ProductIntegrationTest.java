package com.marketplace.it;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.marketplace.products.CreateProductHandler;
import com.marketplace.products.GetProductsHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Integration tests for Product API using real handlers (targeting LocalStack).
 * Note: Assumes LocalStack and Redis are running and initialized.
 */
public class ProductIntegrationTest {

    private static CreateProductHandler createHandler;
    private static GetProductsHandler getHandler;

    @Mock
    private Context context;

    @Mock
    private LambdaLogger logger;

    @BeforeAll
    public static void setup() {
        // Set region for AWS SDK
        System.setProperty("aws.region", "us-east-1");
        // Disable X-Ray for tests to avoid context missing exceptions
        System.setProperty("com.amazonaws.xray.strategy.contextMissingStrategy", "LOG_ERROR");
        
        // Handlers will use default configuration (Endpoint Utils) targeting LocalStack/docker0 IP
        createHandler = new CreateProductHandler();
        getHandler = new GetProductsHandler();
    }

    @BeforeEach
    public void init() {
        MockitoAnnotations.openMocks(this);
        when(context.getLogger()).thenReturn(logger);
    }

    /**
     * Tests the end-to-end flow of creating and then retrieving a product.
     */
    @Test
    public void shouldCreateAndRetrieveProduct() {
        // 1. Create a product
        String productJson = "{\"name\": \"IT Product\", \"price\": 299.99, \"category\": \"IT\"}";
        APIGatewayProxyRequestEvent createRequest = new APIGatewayProxyRequestEvent()
                .withBody(productJson);

        APIGatewayProxyResponseEvent createResponse = createHandler.handleRequest(createRequest, context);
        assertThat(createResponse.getStatusCode()).isEqualTo(201);

        // 2. Retrieve all products and check if the new one is there
        APIGatewayProxyRequestEvent getRequest = new APIGatewayProxyRequestEvent();
        APIGatewayProxyResponseEvent getResponse = getHandler.handleRequest(getRequest, context);
        
        assertThat(getResponse.getStatusCode()).isEqualTo(200);
        assertThat(getResponse.getBody()).contains("IT Product");
    }
}

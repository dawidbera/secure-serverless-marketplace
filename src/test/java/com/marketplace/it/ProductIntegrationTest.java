package com.marketplace.it;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.marketplace.products.CreateProductHandler;
import com.marketplace.products.GetProductsHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Product API using real handlers (targeting LocalStack).
 * Note: Assumes LocalStack and Redis are running and initialized.
 */
public class ProductIntegrationTest {

    private static CreateProductHandler createHandler;
    private static GetProductsHandler getHandler;

    @Mock
    private Context context;

    @BeforeAll
    public static void setup() {
        // Handlers will use default configuration (Endpoint Utils) targeting LocalStack/docker0 IP
        createHandler = new CreateProductHandler();
        getHandler = new GetProductsHandler();
    }

    /**
     * Tests the end-to-end flow of creating and then retrieving a product.
     */
    @Test
    public void shouldCreateAndRetrieveProduct() {
        MockitoAnnotations.openMocks(this);
        
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
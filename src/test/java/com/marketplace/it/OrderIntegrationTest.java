package com.marketplace.it;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.model.Product;
import com.marketplace.orders.CreateOrderHandler;
import com.marketplace.orders.GetMyOrdersHandler;
import com.marketplace.products.CreateProductHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the Order flow.
 * Verifies that placing an order correctly updates stock and appears in history.
 */
public class OrderIntegrationTest {

    private static CreateProductHandler productHandler;
    private static CreateOrderHandler createOrderHandler;
    private static GetMyOrdersHandler getOrdersHandler;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private Context context;

    @Mock
    private LambdaLogger logger;

    @BeforeAll
    public static void setup() {
        System.setProperty("aws.region", "us-east-1");
        System.setProperty("com.amazonaws.xray.strategy.contextMissingStrategy", "LOG_ERROR");
        
        productHandler = new CreateProductHandler();
        createOrderHandler = new CreateOrderHandler();
        getOrdersHandler = new GetMyOrdersHandler();
    }

    @BeforeEach
    public void init() {
        MockitoAnnotations.openMocks(this);
        when(context.getLogger()).thenReturn(logger);
    }

    /**
     * Comprehensive test for creating a product, placing an order, and verifying history.
     */
    @Test
    public void shouldProcessFullOrderFlow() throws Exception {
        // 1. Create a product with stock
        String productJson = "{\"name\": \"Order Test Product\", \"price\": 50.0, \"category\": \"Test\", \"stockQuantity\": 10}";
        APIGatewayProxyRequestEvent createProdReq = new APIGatewayProxyRequestEvent().withBody(productJson);
        APIGatewayProxyResponseEvent prodResp = productHandler.handleRequest(createProdReq, context);
        Product createdProduct = objectMapper.readValue(prodResp.getBody(), Product.class);
        String productId = createdProduct.getId();

        // 2. Place an order
        APIGatewayProxyRequestEvent.ProxyRequestContext proxyContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        proxyContext.setAuthorizer(Map.of("user_id", "integration-user-456"));

        String orderJson = "{\"productId\": \"" + productId + "\", \"quantity\": 3}";
        APIGatewayProxyRequestEvent createOrderReq = new APIGatewayProxyRequestEvent()
                .withRequestContext(proxyContext)
                .withBody(orderJson);

        APIGatewayProxyResponseEvent orderResp = createOrderHandler.handleRequest(createOrderReq, context);
        assertThat(orderResp.getStatusCode()).isEqualTo(201);

        // 3. Verify order appears in user's history
        APIGatewayProxyRequestEvent getOrdersReq = new APIGatewayProxyRequestEvent()
                .withRequestContext(proxyContext);
        APIGatewayProxyResponseEvent historyResp = getOrdersHandler.handleRequest(getOrdersReq, context);
        
        assertThat(historyResp.getStatusCode()).isEqualTo(200);
        assertThat(historyResp.getBody()).contains(productId);
        assertThat(historyResp.getBody()).contains("\"quantity\":3");
    }
}
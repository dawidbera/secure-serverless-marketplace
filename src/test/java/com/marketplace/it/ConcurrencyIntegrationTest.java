package com.marketplace.it;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.model.Product;
import com.marketplace.orders.CreateOrderHandler;
import com.marketplace.products.CreateProductHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Integration test for verifying Concurrency and Optimistic Locking.
 */
public class ConcurrencyIntegrationTest {

    private static CreateProductHandler productHandler;
    private static CreateOrderHandler createOrderHandler;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private Context context;

    @Mock
    private LambdaLogger logger;

    /**
     * Sets up the global environment before all integration tests.
     */
    @BeforeAll
    public static void setup() {
        System.setProperty("aws.region", "us-east-1");
        System.setProperty("aws.accessKeyId", "test");
        System.setProperty("aws.secretAccessKey", "test");
        productHandler = new CreateProductHandler();
        createOrderHandler = new CreateOrderHandler();
    }

    /**
     * Initializes mocks before each test execution.
     */
    @BeforeEach
    public void init() {
        MockitoAnnotations.openMocks(this);
        when(context.getLogger()).thenReturn(logger);
    }

    /**
     * Attempts to place multiple orders concurrently for the same item.
     * Expects some to fail with 409 Conflict if they hit the race condition.
     */
    @Test
    public void shouldHandleConcurrentOrdersWithOptimisticLocking() throws Exception {
        // 1. Create a product with stock 10
        String productJson = "{\"name\": \"Race Condition Product\", \"price\": 10.0, \"category\": \"Race\", \"stockQuantity\": 10}";
        APIGatewayProxyRequestEvent createProdReq = new APIGatewayProxyRequestEvent().withBody(productJson);
        APIGatewayProxyResponseEvent prodResp = productHandler.handleRequest(createProdReq, context);
        Product createdProduct = objectMapper.readValue(prodResp.getBody(), Product.class);
        String productId = createdProduct.getId();

        // 2. Prepare 5 concurrent order requests
        int concurrentRequests = 5;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        
        List<CompletableFuture<APIGatewayProxyResponseEvent>> futures = new ArrayList<>();
        
        for (int i = 0; i < concurrentRequests; i++) {
            final int userIdSuffix = i;
            futures.add(CompletableFuture.supplyAsync(() -> {
                APIGatewayProxyRequestEvent.ProxyRequestContext proxyContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
                proxyContext.setAuthorizer(Map.of("user_id", "user-" + userIdSuffix));

                String orderJson = "{\"productId\": \"" + productId + "\", \"quantity\": 5}";
                APIGatewayProxyRequestEvent orderReq = new APIGatewayProxyRequestEvent()
                        .withRequestContext(proxyContext)
                        .withBody(orderJson);

                return createOrderHandler.handleRequest(orderReq, context);
            }, executor));
        }

        // 3. Wait for all to complete
        List<APIGatewayProxyResponseEvent> responses = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        // 4. Analysis
        long successCount = responses.stream().filter(r -> r.getStatusCode() == 201).count();
        long conflictCount = responses.stream().filter(r -> r.getStatusCode() == 409).count();

        executor.shutdown();

        // Since stock is 10 and each order wants 5, at most 2 should succeed.
        assertThat(successCount).isLessThanOrEqualTo(2);
        System.out.println("Concurrent Test Results - Success: " + successCount + ", Conflict: " + conflictCount);
    }
}
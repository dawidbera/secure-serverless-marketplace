package com.marketplace.orders;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.model.Order;
import com.marketplace.model.Product;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import com.amazonaws.xray.interceptors.TracingInterceptor;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import com.marketplace.utils.ClientUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lambda handler for placing an order with optimistic locking.
 */
public class CreateOrderHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final ObjectMapper objectMapper;

    /**
     * Initializes the DynamoDB client and other dependencies.
     */
    public CreateOrderHandler() {
        this(null, null);
    }

    /**
     * Constructor for dependency injection, used primarily for testing.
     *
     * @param dynamoDbClient The DynamoDB client.
     * @param tableName      The DynamoDB table name.
     */
    CreateOrderHandler(DynamoDbClient dynamoDbClient, String tableName) {
        ClientOverrideConfiguration clientConfig = ClientUtils.getXRayConfig().toBuilder()
                .retryPolicy(RetryPolicy.builder()
                        .numRetries(3)
                        .backoffStrategy(BackoffStrategy.defaultStrategy())
                        .build())
                .build();
        
        this.dynamoDbClient = dynamoDbClient != null ? dynamoDbClient : 
                ClientUtils.configureEndpoint(DynamoDbClient.builder())
                .overrideConfiguration(clientConfig)
                .build();
        
        this.tableName = tableName != null ? tableName : System.getenv("TABLE_NAME");
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            // Retrieve userId from Lambda Authorizer context
            Map<String, Object> authorizerContext = input.getRequestContext().getAuthorizer();
            String userId = (authorizerContext != null) ? (String) authorizerContext.get("user_id") : "test-user-123";

            if (userId == null) {
                userId = "test-user-123"; // Final fallback
            }

            Order orderRequest = objectMapper.readValue(input.getBody(), Order.class);
            orderRequest.setUserId(userId);
            
            // 1. Fetch current product state
            Map<String, AttributeValue> productKey = new HashMap<>();
            productKey.put("PK", AttributeValue.builder().s("PROD#" + orderRequest.getProductId()).build());
            productKey.put("SK", AttributeValue.builder().s("METADATA").build());

            GetItemResponse productResponse = dynamoDbClient.getItem(GetItemRequest.builder()
                    .tableName(tableName)
                    .key(productKey)
                    .build());

            if (!productResponse.hasItem()) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(404)
                        .withBody("{\"error\": \"Product not found\"}");
            }

            Map<String, AttributeValue> productItem = productResponse.item();
            int currentStock = Integer.parseInt(productItem.get("stockQuantity").n());
            int currentVersion = Integer.parseInt(productItem.get("version").n());

            if (currentStock < orderRequest.getQuantity()) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withBody("{\"error\": \"Insufficient stock\"}");
            }

            // 2. Prepare Transaction
            String orderId = UUID.randomUUID().toString();
            long timestamp = Instant.now().getEpochSecond();

            // Update Product (Decrement stock, Increment version)
            Update productUpdate = Update.builder()
                    .tableName(tableName)
                    .key(productKey)
                    .updateExpression("SET stockQuantity = stockQuantity - :q, version = version + :inc")
                    .conditionExpression("version = :v AND stockQuantity >= :q")
                    .expressionAttributeValues(Map.of(
                            ":q", AttributeValue.builder().n(String.valueOf(orderRequest.getQuantity())).build(),
                            ":inc", AttributeValue.builder().n("1").build(),
                            ":v", AttributeValue.builder().n(String.valueOf(currentVersion)).build()
                    ))
                    .build();

            // Put Order Item
            Map<String, AttributeValue> orderItem = new HashMap<>();
            orderItem.put("PK", AttributeValue.builder().s("PROD#" + orderRequest.getProductId()).build());
            orderItem.put("SK", AttributeValue.builder().s("ORDER#" + orderId).build());
            orderItem.put("orderId", AttributeValue.builder().s(orderId).build());
            orderItem.put("productId", AttributeValue.builder().s(orderRequest.getProductId()).build());
            orderItem.put("userId", AttributeValue.builder().s(orderRequest.getUserId()).build());
            orderItem.put("quantity", AttributeValue.builder().n(String.valueOf(orderRequest.getQuantity())).build());
            orderItem.put("timestamp", AttributeValue.builder().n(String.valueOf(timestamp)).build());
            // Attribute for GSI: userId
            orderItem.put("GSI_PK", AttributeValue.builder().s("USER#" + orderRequest.getUserId()).build());

            Put orderPut = Put.builder()
                    .tableName(tableName)
                    .item(orderItem)
                    .build();

            TransactWriteItemsRequest transaction = TransactWriteItemsRequest.builder()
                    .transactItems(
                            TransactWriteItem.builder().update(productUpdate).build(),
                            TransactWriteItem.builder().put(orderPut).build()
                    )
                    .build();

            dynamoDbClient.transactWriteItems(transaction);

            orderRequest.setOrderId(orderId);
            orderRequest.setTimestamp(timestamp);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(201)
                    .withBody(objectMapper.writeValueAsString(orderRequest));

        } catch (TransactionCanceledException e) {
            context.getLogger().log("Transaction cancelled: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(409) // Conflict
                    .withBody("{\"error\": \"Concurrent update or insufficient stock\"}");
        } catch (Exception e) {
            context.getLogger().log("Error creating order: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\": \"Could not process order\"}");
        }
    }
}

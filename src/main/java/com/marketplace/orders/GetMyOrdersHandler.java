package com.marketplace.orders;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.model.Order;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import com.amazonaws.xray.interceptors.TracingInterceptor;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import com.marketplace.utils.ClientUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lambda handler for retrieving orders belonging to the authenticated user.
 */
public class GetMyOrdersHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final ObjectMapper objectMapper;

    /**
     * Initializes the DynamoDB client and other dependencies.
     */
    public GetMyOrdersHandler() {
        this(null, null);
    }

    /**
     * Constructor for dependency injection, used primarily for testing.
     *
     * @param dynamoDbClient The DynamoDB client.
     * @param tableName      The DynamoDB table name.
     */
    GetMyOrdersHandler(DynamoDbClient dynamoDbClient, String tableName) {
        ClientOverrideConfiguration xRayConfig = ClientUtils.getXRayConfig();
        
        this.dynamoDbClient = dynamoDbClient != null ? dynamoDbClient : 
                ClientUtils.configureEndpoint(DynamoDbClient.builder())
                .overrideConfiguration(xRayConfig)
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

            // Query GSI2 for user orders
            QueryRequest queryRequest = QueryRequest.builder()
                    .tableName(tableName)
                    .indexName("GSI2")
                    .keyConditionExpression("GSI_PK = :u")
                    .expressionAttributeValues(Map.of(
                            ":u", AttributeValue.builder().s("USER#" + userId).build()
                    ))
                    .scanIndexForward(false) // Newest orders first
                    .build();

            QueryResponse queryResponse = dynamoDbClient.query(queryRequest);
            List<Order> orders = new ArrayList<>();

            for (Map<String, AttributeValue> item : queryResponse.items()) {
                Order order = new Order();
                order.setOrderId(item.get("orderId").s());
                order.setProductId(item.get("productId").s());
                order.setUserId(item.get("userId").s());
                order.setQuantity(Integer.parseInt(item.get("quantity").n()));
                order.setTimestamp(Long.parseLong(item.get("timestamp").n()));
                orders.add(order);
            }

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(headers)
                    .withBody(objectMapper.writeValueAsString(orders));

        } catch (Exception e) {
            context.getLogger().log("Error fetching user orders: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\": \"Could not fetch orders\"}");
        }
    }
}

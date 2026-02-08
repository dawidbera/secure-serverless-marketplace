package com.marketplace.products;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.model.Product;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lambda handler for creating a new product in the marketplace.
 */
public class CreateProductHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final ObjectMapper objectMapper;

    /**
     * Initializes the DynamoDB client and other dependencies.
     */
    public CreateProductHandler() {
        this.dynamoDbClient = DynamoDbClient.builder().build();
        this.tableName = System.getenv("TABLE_NAME");
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Handles the POST request to create a product.
     *
     * @param input   The API Gateway proxy request event.
     * @param context The Lambda execution context.
     * @return The API Gateway proxy response event.
     */
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            Product product = objectMapper.readValue(input.getBody(), Product.class);
            if (product.getId() == null) {
                product.setId(UUID.randomUUID().toString());
            }

            Map<String, AttributeValue> item = new HashMap<>();
            item.put("PK", AttributeValue.builder().s("PROD#" + product.getId()).build());
            item.put("SK", AttributeValue.builder().s("METADATA").build());
            item.put("id", AttributeValue.builder().s(product.getId()).build());
            item.put("name", AttributeValue.builder().s(product.getName()).build());
            item.put("price", AttributeValue.builder().n(String.valueOf(product.getPrice())).build());
            item.put("category", AttributeValue.builder().s(product.getCategory()).build());

            PutItemRequest putItemRequest = PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .build();

            dynamoDbClient.putItem(putItemRequest);

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(201)
                    .withHeaders(headers)
                    .withBody(objectMapper.writeValueAsString(product));

        } catch (Exception e) {
            context.getLogger().log("Error creating product: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\": \"Could not create product\"}");
        }
    }
}
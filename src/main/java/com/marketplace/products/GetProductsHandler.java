package com.marketplace.products;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.model.Product;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lambda handler for retrieving all products from the marketplace.
 */
public class GetProductsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final ObjectMapper objectMapper;

    /**
     * Initializes the DynamoDB client and other dependencies.
     */
    public GetProductsHandler() {
        this.dynamoDbClient = DynamoDbClient.builder().build();
        this.tableName = System.getenv("TABLE_NAME");
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Handles the GET request to list all products.
     *
     * @param input   The API Gateway proxy request event.
     * @param context The Lambda execution context.
     * @return The API Gateway proxy response event containing the list of products.
     */
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            ScanRequest scanRequest = ScanRequest.builder()
                    .tableName(tableName)
                    .filterExpression("begins_with(PK, :prodPrefix) AND SK = :metadata")
                    .expressionAttributeValues(Map.of(
                            ":prodPrefix", AttributeValue.builder().s("PROD#").build(),
                            ":metadata", AttributeValue.builder().s("METADATA").build()
                    ))
                    .build();

            ScanResponse scanResponse = dynamoDbClient.scan(scanRequest);
            List<Product> products = new ArrayList<>();

            for (Map<String, AttributeValue> item : scanResponse.items()) {
                Product product = new Product();
                product.setId(item.get("id").s());
                product.setName(item.get("name").s());
                product.setPrice(Double.parseDouble(item.get("price").n()));
                product.setCategory(item.get("category").s());
                products.add(product);
            }

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(headers)
                    .withBody(objectMapper.writeValueAsString(products));

        } catch (Exception e) {
            context.getLogger().log("Error fetching products: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\": \"Could not fetch products\"}");
        }
    }
}
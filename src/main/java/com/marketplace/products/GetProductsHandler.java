package com.marketplace.products;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.model.Product;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import com.amazonaws.xray.interceptors.TracingInterceptor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lambda handler for retrieving all products from the marketplace.
 */
public class GetProductsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDbClient;
    private final KmsClient kmsClient;
    private final String tableName;
    private final ObjectMapper objectMapper;

    /**
     * Initializes the DynamoDB client and other dependencies.
     */
    public GetProductsHandler() {
        ClientOverrideConfiguration xRayConfig = ClientOverrideConfiguration.builder()
                .addExecutionInterceptor(new TracingInterceptor())
                .build();

        this.dynamoDbClient = DynamoDbClient.builder()
                .overrideConfiguration(xRayConfig)
                .build();

        this.kmsClient = KmsClient.builder()
                .overrideConfiguration(xRayConfig)
                .build();

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
                if (item.containsKey("version")) {
                    product.setVersion(Integer.parseInt(item.get("version").n()));
                }

                // KMS Decryption for sensitive supplier email
                if (item.containsKey("supplierEmail")) {
                    byte[] decodedCiphertext = Base64.getDecoder().decode(item.get("supplierEmail").s());
                    DecryptRequest decryptRequest = DecryptRequest.builder()
                            .ciphertextBlob(SdkBytes.fromByteArray(decodedCiphertext))
                            .build();
                    
                    DecryptResponse decryptResponse = kmsClient.decrypt(decryptRequest);
                    product.setSupplierEmail(decryptResponse.plaintext().asUtf8String());
                }

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
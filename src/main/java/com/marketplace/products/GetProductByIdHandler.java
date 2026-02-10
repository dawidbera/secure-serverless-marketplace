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
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import com.marketplace.utils.ClientUtils;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Lambda handler for retrieving a specific product by its ID.
 */
public class GetProductByIdHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDbClient;
    private final KmsClient kmsClient;
    private final String tableName;
    private final ObjectMapper objectMapper;

    /**
     * Initializes the DynamoDB client and other dependencies.
     */
    public GetProductByIdHandler() {
        ClientOverrideConfiguration xRayConfig = ClientUtils.getXRayConfig();

        this.dynamoDbClient = ClientUtils.configureEndpoint(DynamoDbClient.builder())
                .overrideConfiguration(xRayConfig)
                .build();

        this.kmsClient = ClientUtils.configureEndpoint(KmsClient.builder())
                .overrideConfiguration(xRayConfig)
                .build();

        this.tableName = System.getenv("TABLE_NAME");
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Handles the GET request to fetch a product by ID.
     *
     * @param input   The API Gateway proxy request event containing the path parameter 'id'.
     * @param context The Lambda execution context.
     * @return The API Gateway proxy response event with the product details or an error message.
     */
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            String productId = input.getPathParameters().get("id");
            
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("PK", AttributeValue.builder().s("PROD#" + productId).build());
            key.put("SK", AttributeValue.builder().s("METADATA").build());

            GetItemRequest getItemRequest = GetItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .build();

            GetItemResponse getItemResponse = dynamoDbClient.getItem(getItemRequest);

            if (!getItemResponse.hasItem()) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(404)
                        .withBody("{\"error\": \"Product not found\"}");
            }

            Map<String, AttributeValue> item = getItemResponse.item();
            Product product = new Product();
            product.setId(item.get("id").s());
            product.setName(item.get("name").s());
            product.setPrice(Double.parseDouble(item.get("price").n()));
            product.setCategory(item.get("category").s());
            if (item.containsKey("version")) {
                product.setVersion(Integer.parseInt(item.get("version").n()));
            }
            if (item.containsKey("stockQuantity")) {
                product.setStockQuantity(Integer.parseInt(item.get("stockQuantity").n()));
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

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(headers)
                    .withBody(objectMapper.writeValueAsString(product));

        } catch (Exception e) {
            context.getLogger().log("Error fetching product: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\": \"Could not fetch product\"}");
        }
    }
}
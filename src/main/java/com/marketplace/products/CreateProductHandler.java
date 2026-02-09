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
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.EncryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptResponse;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lambda handler for creating a new product in the marketplace.
 */
public class CreateProductHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDbClient;
    private final KmsClient kmsClient;
    private final SecretsManagerClient secretsManagerClient;
    private final String tableName;
    private final String kmsKeyId;
    private final String logisticsSecretArn;
    private final ObjectMapper objectMapper;

    /**
     * Initializes the DynamoDB client and other dependencies.
     */
    public CreateProductHandler() {
        ClientOverrideConfiguration xRayConfig = ClientOverrideConfiguration.builder()
                .addExecutionInterceptor(new TracingInterceptor())
                .build();
        
        this.dynamoDbClient = DynamoDbClient.builder()
                .overrideConfiguration(xRayConfig)
                .build();
        
        this.kmsClient = KmsClient.builder()
                .overrideConfiguration(xRayConfig)
                .build();

        this.secretsManagerClient = SecretsManagerClient.builder()
                .overrideConfiguration(xRayConfig)
                .build();

        this.tableName = System.getenv("TABLE_NAME");
        this.kmsKeyId = System.getenv("KMS_KEY_ID");
        this.logisticsSecretArn = System.getenv("LOGISTICS_SECRET_ARN");
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
            // Demonstrate Secrets Manager usage: Fetch logistics API key
            GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                    .secretId(logisticsSecretArn)
                    .build();
            GetSecretValueResponse secretValueResponse = secretsManagerClient.getSecretValue(getSecretValueRequest);
            context.getLogger().log("Successfully retrieved logistics secret: " + secretValueResponse.name());

            Product product = objectMapper.readValue(input.getBody(), Product.class);
            if (product.getId() == null) {
                product.setId(UUID.randomUUID().toString());
            }
            // Initialize version for new products
            product.setVersion(1);

            Map<String, AttributeValue> item = new HashMap<>();
            item.put("PK", AttributeValue.builder().s("PROD#" + product.getId()).build());
            item.put("SK", AttributeValue.builder().s("METADATA").build());
            item.put("id", AttributeValue.builder().s(product.getId()).build());
            item.put("name", AttributeValue.builder().s(product.getName()).build());
            item.put("price", AttributeValue.builder().n(String.valueOf(product.getPrice())).build());
            item.put("category", AttributeValue.builder().s(product.getCategory()).build());
            item.put("version", AttributeValue.builder().n(String.valueOf(product.getVersion())).build());

            // KMS Encryption for sensitive supplier email
            if (product.getSupplierEmail() != null) {
                EncryptRequest encryptRequest = EncryptRequest.builder()
                        .keyId(kmsKeyId)
                        .plaintext(SdkBytes.fromUtf8String(product.getSupplierEmail()))
                        .build();
                
                EncryptResponse encryptResponse = kmsClient.encrypt(encryptRequest);
                String ciphertext = Base64.getEncoder().encodeToString(encryptResponse.ciphertextBlob().asByteArray());
                item.put("supplierEmail", AttributeValue.builder().s(ciphertext).build());
            }

            PutItemRequest putItemRequest = PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .conditionExpression("attribute_not_exists(PK)")
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
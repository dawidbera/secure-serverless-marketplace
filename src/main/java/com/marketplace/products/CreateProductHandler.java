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
import com.marketplace.utils.ClientUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

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
    private static JedisPool jedisPool;

    /**
     * Initializes the DynamoDB client and other dependencies.
     */
    public CreateProductHandler() {
        this(null, null, null, null, null, null);
    }

    /**
     * Constructor for dependency injection, used primarily for testing.
     *
     * @param dynamoDbClient       The DynamoDB client.
     * @param kmsClient            The KMS client.
     * @param secretsManagerClient The Secrets Manager client.
     * @param tableName           The DynamoDB table name.
     * @param kmsKeyId            The KMS Key ID.
     * @param logisticsSecretArn  The logistics secret ARN.
     */
    public CreateProductHandler(DynamoDbClient dynamoDbClient, KmsClient kmsClient, 
                         SecretsManagerClient secretsManagerClient, String tableName, 
                         String kmsKeyId, String logisticsSecretArn) {
        ClientOverrideConfiguration xRayConfig = ClientUtils.getXRayConfig();
        
        this.dynamoDbClient = dynamoDbClient != null ? dynamoDbClient : 
                ClientUtils.configureEndpoint(DynamoDbClient.builder())
                .overrideConfiguration(xRayConfig)
                .build();
        
        this.kmsClient = kmsClient != null ? kmsClient : 
                ClientUtils.configureEndpoint(KmsClient.builder())
                .overrideConfiguration(xRayConfig)
                .build();

        this.secretsManagerClient = secretsManagerClient != null ? secretsManagerClient : 
                ClientUtils.configureEndpoint(SecretsManagerClient.builder())
                .overrideConfiguration(xRayConfig)
                .build();

        this.tableName = tableName != null ? tableName : System.getenv("TABLE_NAME");
        this.kmsKeyId = kmsKeyId != null ? kmsKeyId : System.getenv("KMS_KEY_ID");
        this.logisticsSecretArn = logisticsSecretArn != null ? logisticsSecretArn : System.getenv("LOGISTICS_SECRET_ARN");
        this.objectMapper = new ObjectMapper();
        initializeRedisPool();
    }

    /**
     * Validates the product details.
     *
     * @param product The product to validate.
     * @return An error message if validation fails, otherwise null.
     */
    private String validateProduct(Product product) {
        if (product.getName() == null || product.getName().trim().isEmpty()) {
            return "Product name is required";
        }
        if (product.getPrice() <= 0) {
            return "Product price must be greater than zero";
        }
        if (product.getCategory() == null || product.getCategory().trim().isEmpty()) {
            return "Product category is required";
        }
        if (product.getStockQuantity() < 0) {
            return "Stock quantity cannot be negative";
        }
        return null;
    }

    /**
     * Initializes the Redis connection pool using environment variables.
     */
    private void initializeRedisPool() {
        if (jedisPool == null) {
            String redisHost = System.getenv("REDIS_HOST");
            String redisPort = System.getenv("REDIS_PORT");
            if (redisHost != null && redisPort != null) {
                jedisPool = new JedisPool(new JedisPoolConfig(), redisHost, Integer.parseInt(redisPort));
            }
        }
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
            
            // Validate input
            String validationError = validateProduct(product);
            if (validationError != null) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withBody("{\"error\": \"" + validationError + "\"}");
            }

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
            item.put("stockQuantity", AttributeValue.builder().n(String.valueOf(product.getStockQuantity())).build());

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

            // Invalidate Redis Cache
            if (jedisPool != null) {
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.flushAll(); // Simple eviction for the prototype
                    context.getLogger().log("Redis cache invalidated.");
                } catch (Exception e) {
                    context.getLogger().log("Redis eviction error: " + e.getMessage());
                }
            }

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(201)
                    .withHeaders(headers)
                    .withBody(objectMapper.writeValueAsString(product));

        } catch (Exception e) {
            context.getLogger().log("Error creating product: " + e.getMessage());
            e.printStackTrace(); // Crucial for seeing the error in Maven logs
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\": \"Could not create product\", \"details\": \"" + e.toString() + "\"}");
        }
    }
}
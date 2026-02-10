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
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import com.marketplace.utils.ClientUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

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
    private final SsmClient ssmClient;
    private final String tableName;
    private final ObjectMapper objectMapper;
    private static JedisPool jedisPool;

    /**
     * Initializes the DynamoDB client and other dependencies.
     */
    public GetProductsHandler() {
        this(null, null, null, null);
    }

    /**
     * Constructor for dependency injection, used primarily for testing.
     *
     * @param dynamoDbClient The DynamoDB client.
     * @param kmsClient      The KMS client.
     * @param ssmClient      The SSM client.
     * @param tableName      The DynamoDB table name.
     */
    GetProductsHandler(DynamoDbClient dynamoDbClient, KmsClient kmsClient, 
                       SsmClient ssmClient, String tableName) {
        ClientOverrideConfiguration xRayConfig = ClientUtils.getXRayConfig();

        this.dynamoDbClient = dynamoDbClient != null ? dynamoDbClient :
                ClientUtils.configureEndpoint(DynamoDbClient.builder())
                .overrideConfiguration(xRayConfig)
                .build();

        this.kmsClient = kmsClient != null ? kmsClient :
                ClientUtils.configureEndpoint(KmsClient.builder())
                .overrideConfiguration(xRayConfig)
                .build();

        this.ssmClient = ssmClient != null ? ssmClient :
                ClientUtils.configureEndpoint(SsmClient.builder())
                .overrideConfiguration(xRayConfig)
                .build();

        if (tableName != null) {
            this.tableName = tableName;
        } else {
            String ssmParamName = System.getenv("SSM_PARAMETER_NAME");
            if (ssmParamName != null) {
                this.tableName = ssmClient.getParameter(GetParameterRequest.builder()
                        .name(ssmParamName)
                        .build()).parameter().value();
            } else {
                this.tableName = System.getenv("TABLE_NAME");
            }
        }
        
        this.objectMapper = new ObjectMapper();
        initializeRedisPool();
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
     * Handles the GET request to list all products.
     *
     * @param input   The API Gateway proxy request event.
     * @param context The Lambda execution context.
     * @return The API Gateway proxy response event containing the list of products.
     */
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            Map<String, String> queryParams = input.getQueryStringParameters();
            String category = (queryParams != null) ? queryParams.get("category") : null;
            
            String cacheKey = (category != null) ? "products:cat:" + category : "products:all";

            // 1. Try to fetch from Redis Cache
            if (jedisPool != null) {
                try (Jedis jedis = jedisPool.getResource()) {
                    String cachedProducts = jedis.get(cacheKey);
                    if (cachedProducts != null) {
                        context.getLogger().log("Cache hit for key: " + cacheKey);
                        return createResponse(200, cachedProducts);
                    }
                } catch (Exception e) {
                    context.getLogger().log("Redis error: " + e.getMessage());
                }
            }

            // 2. Fallback to DynamoDB
            List<Map<String, AttributeValue>> items;

            if (category != null && !category.isEmpty()) {
                // Use GSI1 for efficient category filtering
                QueryRequest queryRequest = QueryRequest.builder()
                        .tableName(tableName)
                        .indexName("GSI1")
                        .keyConditionExpression("category = :cat")
                        .expressionAttributeValues(Map.of(
                                ":cat", AttributeValue.builder().s(category).build()
                        ))
                        .build();
                items = dynamoDbClient.query(queryRequest).items();
            } else {
                // Fallback to scan if no category is provided (standard behavior)
                ScanRequest scanRequest = ScanRequest.builder()
                        .tableName(tableName)
                        .filterExpression("begins_with(PK, :prodPrefix) AND SK = :metadata")
                        .expressionAttributeValues(Map.of(
                                ":prodPrefix", AttributeValue.builder().s("PROD#").build(),
                                ":metadata", AttributeValue.builder().s("METADATA").build()
                        ))
                        .build();
                items = dynamoDbClient.scan(scanRequest).items();
            }

            List<Product> products = new ArrayList<>();

            for (Map<String, AttributeValue> item : items) {
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

                products.add(product);
            }

            String productsJson = objectMapper.writeValueAsString(products);

            // 3. Save to Redis Cache (with 60s TTL)
            if (jedisPool != null) {
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.setex(cacheKey, 60, productsJson);
                    context.getLogger().log("Cache updated for key: " + cacheKey);
                } catch (Exception e) {
                    context.getLogger().log("Redis save error: " + e.getMessage());
                }
            }

            return createResponse(200, productsJson);

        } catch (Exception e) {
            context.getLogger().log("Error fetching products: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\": \"Could not fetch products\"}");
        }
    }

    private APIGatewayProxyResponseEvent createResponse(int statusCode, String body) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withBody(body);
    }
}
package com.marketplace.it;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.model.Product;
import com.marketplace.products.CreateProductHandler;
import com.marketplace.utils.ClientUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.CreateKeyResponse;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Security Integration Test to verify that sensitive PII data 
 * is encrypted at rest in DynamoDB using AWS KMS.
 */
public class SecurityEncryptionTest {

    private static CreateProductHandler createHandler;
    private static DynamoDbClient dynamoDbClient;
    private static String tableName;
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
        
        String endpoint = System.getenv("AWS_ENDPOINT_URL");
        if (endpoint == null) endpoint = "http://localhost:4566";
        System.setProperty("AWS_ENDPOINT_URL", endpoint);
        
        tableName = System.getenv("TABLE_NAME");
        if (tableName == null) tableName = "Products";
        System.setProperty("TABLE_NAME", tableName);

        DynamoDbClient db = ClientUtils.configureEndpoint(DynamoDbClient.builder()).build();
        KmsClient kms = ClientUtils.configureEndpoint(KmsClient.builder()).build();
        SecretsManagerClient sm = ClientUtils.configureEndpoint(SecretsManagerClient.builder()).build();

        // 1. Create a real KMS key in LocalStack for this test
        CreateKeyResponse keyResponse = kms.createKey(r -> r.description("Test Key"));
        String keyId = keyResponse.keyMetadata().keyId();
        System.setProperty("KMS_KEY_ID", keyId);

        createHandler = new CreateProductHandler(db, kms, sm, tableName, keyId, "LogisticsApiKey");
        dynamoDbClient = db;
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
     * Verifies that the supplierEmail is encrypted in DynamoDB.
     */
    @Test
    public void shouldEncryptSensitiveDataInDynamoDB() throws Exception {
        // 1. Create a product with sensitive email
        String secretEmail = "secret-supplier@example.com";
        String productJson = "{\"name\": \"Secure Product\", \"price\": 10.0, \"category\": \"Security\", \"supplierEmail\": \"" + secretEmail + "\", \"stockQuantity\": 1}";
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent().withBody(productJson);
        APIGatewayProxyResponseEvent response = createHandler.handleRequest(request, context);
        
        if (response.getStatusCode() != 201) {
            System.err.println("CRITICAL: Integration Test Product Creation Failed!");
            System.err.println("Status Code: " + response.getStatusCode());
            System.err.println("Response Body: " + response.getBody());
        }
        assertThat(response.getStatusCode()).isEqualTo(201);
        
        Product createdProduct = objectMapper.readValue(response.getBody(), Product.class);
        String productId = createdProduct.getId();

        // 2. Directly fetch raw item from DynamoDB
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("PK", AttributeValue.builder().s("PROD#" + productId).build());
        key.put("SK", AttributeValue.builder().s("METADATA").build());

        GetItemResponse rawItemResponse = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .build());

        assertThat(rawItemResponse.hasItem()).isTrue();
        Map<String, AttributeValue> rawItem = rawItemResponse.item();

        // 3. Verify encryption
        String storedEmail = rawItem.get("supplierEmail").s();
        
        System.out.println("Stored Email in DynamoDB (RAW): " + storedEmail);
        
        // The stored value should NOT be the plaintext email
        assertThat(storedEmail).isNotEqualTo(secretEmail);
        
        // It should be a Base64-encoded ciphertext (KMS output)
        assertThat(storedEmail).doesNotContain("@");
        assertThat(storedEmail).hasSizeGreaterThan(20);
    }
}
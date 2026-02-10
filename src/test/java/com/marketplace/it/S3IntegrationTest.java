package com.marketplace.it;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.marketplace.products.S3PreSignedUrlHandler;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import com.marketplace.utils.ClientUtils;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Integration test for S3 Pre-signed URLs.
 */
public class S3IntegrationTest {

    private static S3PreSignedUrlHandler handler;
    private static S3Client s3Client;
    private static String bucketName;

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
        bucketName = System.getenv("ASSETS_BUCKET_NAME");
        if (bucketName == null) bucketName = "marketplace-assets-000000000000";

        handler = new S3PreSignedUrlHandler(null, bucketName);
        s3Client = ClientUtils.configureEndpoint(S3Client.builder())
                .forcePathStyle(true)
                .build();
        try {
            s3Client.createBucket(software.amazon.awssdk.services.s3.model.CreateBucketRequest.builder().bucket(bucketName).build());
        } catch (software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException | software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException e) {
            // Ignore
        }
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
     * Verifies that a generated URL is valid and accessible.
     */
    @Test
    public void shouldGenerateWorkingPresignedUrl() throws Exception {
        String productId = "test-prod-s3";
        String objectKey = "assets/" + productId + "/item.zip";

        // 1. Manually upload a file to S3
        s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build(), RequestBody.fromString("test content"));

        // 2. Get Pre-signed URL via Handler
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withPathParameters(Map.of("id", productId));
        
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
        assertThat(response.getStatusCode()).isEqualTo(200);

        JSONObject body = new JSONObject(response.getBody());
        String downloadUrl = body.getString("downloadUrl");

        // 3. Try to access the URL
        HttpURLConnection connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
        connection.setRequestMethod("GET");
        int responseCode = connection.getResponseCode();

        if (responseCode != 200) {
            System.err.println("Failed URL: " + downloadUrl);
            System.err.println("Response Code: " + responseCode);
        }

        assertThat(responseCode).isEqualTo(200);
    }
}

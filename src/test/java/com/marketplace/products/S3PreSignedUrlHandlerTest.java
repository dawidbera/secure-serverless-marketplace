package com.marketplace.products;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URL;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for S3PreSignedUrlHandler.
 */
@ExtendWith(MockitoExtension.class)
public class S3PreSignedUrlHandlerTest {

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private Context context;

    @Mock
    private LambdaLogger logger;

    @Mock
    private PresignedGetObjectRequest presignedRequest;

    private S3PreSignedUrlHandler handler;

    /**
     * Sets up the test environment before each test.
     */
    @BeforeEach
    public void setUp() {
        lenient().when(context.getLogger()).thenReturn(logger);
        handler = new S3PreSignedUrlHandler(s3Presigner, "TestBucket");
    }

    /**
     * Tests successful generation of a pre-signed URL.
     */
    @Test
    public void shouldGeneratePresignedUrlSuccessfully() throws Exception {
        // Given
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withPathParameters(Map.of("id", "prod-1"));

        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedRequest);
        when(presignedRequest.url()).thenReturn(new URL("https://test-bucket.s3.amazonaws.com/assets/prod-1/item.zip"));

        // When
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("https://test-bucket.s3.amazonaws.com/assets/prod-1/item.zip");
    }
}

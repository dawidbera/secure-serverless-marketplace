package com.marketplace.utils;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import com.amazonaws.xray.interceptors.TracingInterceptor;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Utility class for creating AWS SDK clients with X-Ray and custom endpoint support.
 */
public class ClientUtils {

    /**
     * Provides a default X-Ray configuration for AWS SDK clients.
     * Adds the TracingInterceptor to the client configuration.
     *
     * @return A ClientOverrideConfiguration with X-Ray tracing enabled.
     */
    public static ClientOverrideConfiguration getXRayConfig() {
        return ClientOverrideConfiguration.builder()
                .addExecutionInterceptor(new TracingInterceptor())
                .build();
    }

    /**
     * Configures a client builder with a custom endpoint if the AWS_ENDPOINT_URL
     * environment variable is set. This is useful for local development with LocalStack.
     *
     * @param builder The AWS client builder to configure.
     * @param <B>     The type of the client builder.
     * @return The (potentially) modified client builder.
     */
    public static <B extends software.amazon.awssdk.awscore.client.builder.AwsClientBuilder<B, ?>> B configureEndpoint(B builder) {
        String endpoint = System.getenv("AWS_ENDPOINT_URL");
        if (endpoint == null || endpoint.isEmpty()) {
            endpoint = System.getProperty("AWS_ENDPOINT_URL");
        }
        if (endpoint != null && !endpoint.isEmpty()) {
            System.out.println("Configuring client with endpoint: " + endpoint);
            try {
                builder.endpointOverride(new URI(endpoint));
            } catch (URISyntaxException e) {
                System.err.println("Invalid endpoint URI: " + endpoint);
            }
        } else {
            System.out.println("No AWS_ENDPOINT_URL found, using default.");
        }
        return builder;
    }
}

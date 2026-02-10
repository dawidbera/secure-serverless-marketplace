package com.marketplace.auth;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayCustomAuthorizerEvent;
import com.amazonaws.services.lambda.runtime.events.IamPolicyResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for LambdaAuthorizerHandler.
 */
public class LambdaAuthorizerHandlerTest {

    private final LambdaAuthorizerHandler handler = new LambdaAuthorizerHandler();

    /**
     * Tests that a valid token results in an ALLOW policy.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void shouldAllowValidToken() {
        // Given
        APIGatewayCustomAuthorizerEvent event = APIGatewayCustomAuthorizerEvent.builder()
                .withAuthorizationToken("Bearer allow-me")
                .withMethodArn("arn:aws:execute-api:us-east-1:123456789012:api/prod/GET/products")
                .build();

        // When
        IamPolicyResponse response = handler.handleRequest(event, mock(Context.class));

        // Then
        Map<String, Object> policyDocument = response.getPolicyDocument();
        Object[] statements = (Object[]) policyDocument.get("Statement");
        Map<String, Object> firstStatement = (Map<String, Object>) statements[0];
        String effect = (String) firstStatement.get("Effect");
        
        assertThat(effect).isEqualTo("Allow");
        assertThat(response.getPrincipalId()).isEqualTo("authorized-user-123");
    }

    /**
     * Tests that an invalid token results in a DENY policy.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void shouldDenyInvalidToken() {
        // Given
        APIGatewayCustomAuthorizerEvent event = APIGatewayCustomAuthorizerEvent.builder()
                .withAuthorizationToken("Bearer invalid")
                .withMethodArn("arn:aws:execute-api:us-east-1:123456789012:api/prod/GET/products")
                .build();

        // When
        IamPolicyResponse response = handler.handleRequest(event, mock(Context.class));

        // Then
        Map<String, Object> policyDocument = response.getPolicyDocument();
        Object[] statements = (Object[]) policyDocument.get("Statement");
        Map<String, Object> firstStatement = (Map<String, Object>) statements[0];
        String effect = (String) firstStatement.get("Effect");

        assertThat(effect).isEqualTo("Deny");
    }
}

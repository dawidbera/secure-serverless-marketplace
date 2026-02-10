package com.marketplace.auth;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayCustomAuthorizerEvent;
import com.amazonaws.services.lambda.runtime.events.IamPolicyResponse;

import java.util.Collections;
import java.util.Map;

/**
 * Lambda Authorizer to validate Bearer tokens.
 * In a real-world scenario, this would validate a JWT against a User Pool (e.g., Cognito).
 */
public class LambdaAuthorizerHandler implements RequestHandler<APIGatewayCustomAuthorizerEvent, IamPolicyResponse> {

    /**
     * Handles the authorization request from API Gateway.
     * Validates the Bearer token and returns an IAM policy.
     *
     * @param event   The custom authorizer event from API Gateway.
     * @param context The Lambda execution context.
     * @return An IAM policy response indicating if the request is allowed or denied.
     */
    @Override
    public IamPolicyResponse handleRequest(APIGatewayCustomAuthorizerEvent event, Context context) {
        String token = event.getAuthorizationToken();
        String methodArn = event.getMethodArn();

        // Simple validation logic for demonstration purposes
        // In a production environment, you'd typically check a JWT signature and 'exp' claim.
        String effect = "Deny";
        String principalId = "user";

        if (token != null && token.startsWith("Bearer allow-me")) {
            effect = "Allow";
            principalId = "authorized-user-123";
        }

        return generatePolicy(principalId, effect, methodArn);
    }

    /**
     * Generates an IAM policy response for API Gateway.
     */
    private IamPolicyResponse generatePolicy(String principalId, String effect, String resource) {
        IamPolicyResponse.PolicyDocument policyDocument = IamPolicyResponse.PolicyDocument.builder()
                .withVersion("2012-10-17")
                .withStatement(Collections.singletonList(
                        IamPolicyResponse.Statement.builder()
                                .withAction("execute-api:Invoke")
                                .withEffect(effect)
                                .withResource(Collections.singletonList(resource))
                                .build()
                ))
                .build();

        return IamPolicyResponse.builder()
                .withPrincipalId(principalId)
                .withPolicyDocument(policyDocument)
                // You can also pass context to the backend Lambda (e.g., user role)
                .withContext(Map.of("user_id", principalId, "scope", "products:read"))
                .build();
    }
}

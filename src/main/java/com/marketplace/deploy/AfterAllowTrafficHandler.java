package com.marketplace.deploy;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.services.codedeploy.CodeDeployClient;
import software.amazon.awssdk.services.codedeploy.model.PutLifecycleEventHookExecutionStatusRequest;

import java.util.Map;

/**
 * Lambda function to be used as a PostTraffic hook in CodeDeploy.
 * This function validates the deployment after traffic has been shifted to the new version.
 */
public class AfterAllowTrafficHandler implements RequestHandler<Map<String, Object>, Void> {

    private final CodeDeployClient codeDeployClient;

    /**
     * Default constructor for AfterAllowTrafficHandler.
     * Initializes the CodeDeploy client with default settings.
     */
    public AfterAllowTrafficHandler() {
        this.codeDeployClient = CodeDeployClient.builder().build();
    }

    /**
     * Constructor for AfterAllowTrafficHandler with a custom CodeDeploy client.
     * Primarily used for unit testing.
     *
     * @param codeDeployClient The CodeDeploy client to use.
     */
    public AfterAllowTrafficHandler(CodeDeployClient codeDeployClient) {
        this.codeDeployClient = codeDeployClient;
    }

    /**
     * Handles the PostTraffic lifecycle event from CodeDeploy.
     * Validates the deployment and reports the status back to CodeDeploy.
     *
     * @param event   The event data from CodeDeploy.
     * @param context The Lambda execution context.
     * @return null as expected by the RequestHandler interface for this event.
     */
    @Override
    public Void handleRequest(Map<String, Object> event, Context context) {
        context.getLogger().log("AfterAllowTraffic hook started. Event: " + event);

        String deploymentId = (String) event.get("DeploymentId");
        String lifecycleEventHookExecutionId = (String) event.get("LifecycleEventHookExecutionId");

        try {
            // Perform post-traffic validation logic here
            validateDeployment(context);

            context.getLogger().log("Post-traffic validation succeeded. Reporting Succeeded to CodeDeploy.");
            reportStatus(deploymentId, lifecycleEventHookExecutionId, "Succeeded");
        } catch (Exception e) {
            context.getLogger().log("Post-traffic validation failed: " + e.getMessage());
            reportStatus(deploymentId, lifecycleEventHookExecutionId, "Failed");
        }

        return null;
    }

    /**
     * Validates the deployment after traffic shift.
     *
     * @param context The Lambda execution context.
     */
    private void validateDeployment(Context context) {
        // Mock validation
        context.getLogger().log("Performing post-traffic validation checks...");
    }

    /**
     * Reports the lifecycle event hook execution status to CodeDeploy.
     *
     * @param deploymentId The ID of the deployment.
     * @param hookId       The lifecycle event hook execution ID.
     * @param status       The status to report (e.g., "Succeeded", "Failed").
     */
    private void reportStatus(String deploymentId, String hookId, String status) {
        PutLifecycleEventHookExecutionStatusRequest request = PutLifecycleEventHookExecutionStatusRequest.builder()
                .deploymentId(deploymentId)
                .lifecycleEventHookExecutionId(hookId)
                .status(status)
                .build();
        codeDeployClient.putLifecycleEventHookExecutionStatus(request);
    }
}

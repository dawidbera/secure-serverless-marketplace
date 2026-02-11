package com.marketplace.deploy;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.services.codedeploy.CodeDeployClient;
import software.amazon.awssdk.services.codedeploy.model.PutLifecycleEventHookExecutionStatusRequest;

import java.util.Map;

/**
 * Lambda function to be used as a PreTraffic hook in CodeDeploy.
 * This function validates the deployment before traffic is shifted to the new version.
 */
public class BeforeAllowTrafficHandler implements RequestHandler<Map<String, Object>, Void> {

    private final CodeDeployClient codeDeployClient;

    /**
     * Default constructor for BeforeAllowTrafficHandler.
     * Initializes the CodeDeploy client with default settings.
     */
    public BeforeAllowTrafficHandler() {
        this.codeDeployClient = CodeDeployClient.builder().build();
    }

    /**
     * Constructor for BeforeAllowTrafficHandler with a custom CodeDeploy client.
     * Primarily used for unit testing.
     *
     * @param codeDeployClient The CodeDeploy client to use.
     */
    public BeforeAllowTrafficHandler(CodeDeployClient codeDeployClient) {
        this.codeDeployClient = codeDeployClient;
    }

    /**
     * Handles the PreTraffic lifecycle event from CodeDeploy.
     * Validates the deployment and reports the status back to CodeDeploy.
     *
     * @param event   The event data from CodeDeploy.
     * @param context The Lambda execution context.
     * @return null as expected by the RequestHandler interface for this event.
     */
    @Override
    public Void handleRequest(Map<String, Object> event, Context context) {
        context.getLogger().log("BeforeAllowTraffic hook started. Event: " + event);

        String deploymentId = (String) event.get("DeploymentId");
        String lifecycleEventHookExecutionId = (String) event.get("LifecycleEventHookExecutionId");

        try {
            // Perform validation logic here
            // For example, calling the new Lambda version via its alias
            validateDeployment(context);

            context.getLogger().log("Validation succeeded. Reporting Succeeded to CodeDeploy.");
            reportStatus(deploymentId, lifecycleEventHookExecutionId, "Succeeded");
        } catch (Exception e) {
            context.getLogger().log("Validation failed: " + e.getMessage());
            reportStatus(deploymentId, lifecycleEventHookExecutionId, "Failed");
        }

        return null;
    }

    /**
     * Validates the deployment before traffic shift.
     *
     * @param context The Lambda execution context.
     */
    private void validateDeployment(Context context) {
        // Mock validation: In a real scenario, you might invoke the function
        // or check some resources.
        context.getLogger().log("Performing pre-traffic validation checks...");
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

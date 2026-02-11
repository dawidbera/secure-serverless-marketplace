#!/bin/bash
set -e

# Colors for better readability
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Function to check if a command exists
check_requirement() {
    if ! command -v "$1" &> /dev/null; then
        echo -e "${RED}Error: $1 is not installed or not in PATH.${NC}"
        exit 1
    fi
}

echo -e "${BLUE}--- 1. Checking prerequisites ---${NC}"
check_requirement docker
check_requirement mvn
check_requirement sam
check_requirement awslocal

echo -e "${BLUE}--- 2. Cleaning up previous session ---${NC}"
# Kill any existing SAM local processes and free up port 3001
pkill -f "sam local start-api" || true
# Explicitly kill whatever is on port 3001 (if lsof is available)
if command -v lsof &> /dev/null; then
    lsof -ti:3001 | xargs kill -9 2>/dev/null || true
fi
# Wait a moment for the port to be released
sleep 2

echo -e "${BLUE}--- 3. Detecting host IP address (required for Linux) ---${NC}"
# Get the IP address of the docker0 interface
DOCKER_HOST_IP=$(ip addr show docker0 | grep "inet " | awk '{print $2}' | cut -d/ -f1)
if [ -z "$DOCKER_HOST_IP" ]; then
    echo -e "${RED}Error: Could not detect docker0 IP address.${NC}"
    exit 1
fi
echo "Detected IP: $DOCKER_HOST_IP"

echo -e "${BLUE}--- 4. Updating IP configuration in env.json and template.yaml ---${NC}"
sed -i "s/192.168.25.207/$DOCKER_HOST_IP/g" env.json
sed -i "s/192.168.25.207/$DOCKER_HOST_IP/g" template.yaml

echo -e "${BLUE}--- 5. Starting infrastructure (Docker Compose) ---${NC}"
docker compose up -d

echo -e "${BLUE}--- 6. Building Maven project ---${NC}"
# Build the utils layer first and install it to local maven repo
cd layers/marketplace-utils && mvn clean install -DskipTests && cd ../..
mvn clean package -DskipTests

echo -e "${BLUE}--- 7. Initializing LocalStack resources ---${NC}"
echo "Waiting for LocalStack to be ready..."
until awslocal s3 ls > /dev/null 2>&1; do
  echo -n "."
  sleep 2
done
echo -e "\nCleaning up and creating DynamoDB table, SSM parameters, secrets, and S3 bucket..."

# Delete table if exists to ensure clean state
awslocal dynamodb delete-table --table-name Products 2>/dev/null || true

awslocal dynamodb create-table --table-name Products \
  --attribute-definitions AttributeName=PK,AttributeType=S AttributeName=SK,AttributeType=S AttributeName=category,AttributeType=S AttributeName=price,AttributeType=N AttributeName=GSI_PK,AttributeType=S AttributeName=timestamp,AttributeType=N \
  --key-schema AttributeName=PK,KeyType=HASH AttributeName=SK,KeyType=RANGE \
  --global-secondary-indexes "[{\"IndexName\":\"GSI1\",\"KeySchema\":[{\"AttributeName\":\"category\",\"KeyType\":\"HASH\"},{\"AttributeName\":\"price\",\"KeyType\":\"RANGE\"}],\"Projection\":{\"ProjectionType\":\"ALL\"}},{\"IndexName\":\"GSI2\",\"KeySchema\":[{\"AttributeName\":\"GSI_PK\",\"KeyType\":\"HASH\"},{\"AttributeName\":\"timestamp\",\"KeyType\":\"RANGE\"}],\"Projection\":{\"ProjectionType\":\"ALL\"}}]" \
  --billing-mode PAY_PER_REQUEST

awslocal ssm put-parameter --name "/marketplace/table_name" --type "String" --value "Products" --overwrite
awslocal secretsmanager delete-secret --secret-id "LogisticsApiKey" --force-delete-without-recovery 2>/dev/null || true
awslocal secretsmanager create-secret --name "LogisticsApiKey" --secret-string '{"api_key": "super-secret-key-123"}' || true

echo "Setting up Cognito resources..."
# User Pool
USER_POOL_ID=$(awslocal cognito-idp create-user-pool --pool-name MarketplaceUserPool --query 'UserPool.Id' --output text 2>/dev/null || echo "COGNITO_NOT_SUPPORTED")
echo "User Pool ID: $USER_POOL_ID"

if [ "$USER_POOL_ID" != "COGNITO_NOT_SUPPORTED" ]; then
    # User Pool Client
    CLIENT_ID=$(awslocal cognito-idp create-user-pool-client --user-pool-id $USER_POOL_ID --client-name MarketplaceWebClient --query 'UserPoolClient.ClientId' --output text)
    echo "User Pool Client ID: $CLIENT_ID"

    # Identity Pool
    IDENTITY_POOL_ID=$(awslocal cognito-identity create-identity-pool --identity-pool-name MarketplaceIdentityPool --no-allow-unauthenticated-identities --cognito-identity-providers ProviderName="cognito-idp.us-east-1.amazonaws.com/$USER_POOL_ID",ClientId="$CLIENT_ID" --query 'IdentityPoolId' --output text)
    echo "Identity Pool ID: $IDENTITY_POOL_ID"

    # Export IDs for tests or other scripts if needed
    export USER_POOL_ID
    export CLIENT_ID
    export IDENTITY_POOL_ID
else
    echo -e "${RED}Warning: Cognito is not supported in this LocalStack environment. Skipping Cognito-specific setup.${NC}"
fi

# Check if bucket exists, if not create it
if ! awslocal s3 ls s3://marketplace-assets-000000000000 >/dev/null 2>&1; then
    awslocal s3 mb s3://marketplace-assets-000000000000
fi

echo -e "${BLUE}--- 8. Starting SAM Local API (in background) ---${NC}"
# Start the API on 0.0.0.0 to be accessible from Docker containers
nohup sam local start-api --region us-east-1 --env-vars env.json --port 3001 --host 0.0.0.0 > sam-api-start.log 2>&1 &
SAM_PID=$!
sleep 5 # Give it more time to bind to 0.0.0.0

echo "Waiting for API to start (port 3001)..."
# Health check loop
MAX_RETRIES=30
COUNT=0
until curl -s http://127.0.0.1:3001/products > /dev/null; do
  if ! kill -0 $SAM_PID 2>/dev/null; then
    echo -e "${RED}Error: SAM API failed to start. Check sam-api-start.log${NC}"
    exit 1
  fi
  if [ $COUNT -ge $MAX_RETRIES ]; then
    echo -e "${RED}Error: API startup timed out. Check sam-api-start.log${NC}"
    exit 1
  fi
  echo -n "."
  ((COUNT++))
  sleep 2
done
echo -e "\n${GREEN}API is ready!${NC}"

echo -e "${BLUE}--- 9. Running Java tests (Unit & Integration) ---${NC}"
AWS_ACCESS_KEY_ID=test \
AWS_SECRET_ACCESS_KEY=test \
AWS_REGION=us-east-1 \
AWS_ENDPOINT_URL=http://localhost:4566 \
REDIS_HOST=localhost \
TABLE_NAME=Products \
LOGISTICS_SECRET_ARN=LogisticsApiKey \
KMS_KEY_ID=dummy \
mvn test

echo -e "${BLUE}--- 10. Running shell test scripts ---${NC}"
chmod +x scripts/*.sh
echo -e "\n${GREEN}Running test_cache.sh:${NC}"
./scripts/test_cache.sh

echo -e "\n${GREEN}Running test_assets.sh:${NC}"
./scripts/test_assets.sh

echo -e "\n${BLUE}--- 11. Running Load Tests (k6 via Docker) ---${NC}"
echo "Running performance test for approximately 2 minutes against $DOCKER_HOST_IP..."
docker run --rm -e API_URL="http://$DOCKER_HOST_IP:3001" -v $(pwd)/load-tests:/io -i loadimpact/k6 run /io/performance-test.js

echo -e "\n${BLUE}--- Done! API is still running in the background (PID: $SAM_PID) ---${NC}"
echo "You can stop it by running: kill $SAM_PID"
echo "API logs are available at: secure-serverless-marketplace/sam-api-start.log"

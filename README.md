# Secure Serverless Marketplace

A high-performance, secure backend for a digital marketplace leveraging modern cloud-native patterns.

## 🏗 Architecture
- **Compute:** AWS Lambda (Java 17)
- **API:** Amazon API Gateway (Throttling: 10 RPS, Caching: 60s)
- **Database:** Amazon DynamoDB (Single-table design)
    - **GSI1:** Category-based search (`PK: category`, `SK: price`)
    - **GSI2:** User order history (`PK: GSI_PK`, `SK: timestamp`)
- **Security:** AWS KMS (PII Encryption), Secrets Manager (API Keys), IAM (Least Privilege)
- **Concurrency:** Optimistic Locking with `version` attribute.
- **Infrastructure:** AWS SAM + LocalStack

## 🚀 Getting Started

### Prerequisites
- Docker & Docker Compose
- Java 17+ & Maven
- AWS SAM CLI & `awslocal`

### 1. Start Local Environment
```bash
docker compose up -d
```

### 2. Setup Resources (LocalStack)
Before running the API, configure the infrastructure in LocalStack:
```bash
# Table setup
awslocal dynamodb create-table --table-name Products \
  --attribute-definitions AttributeName=PK,AttributeType=S AttributeName=SK,AttributeType=S AttributeName=category,AttributeType=S AttributeName=price,AttributeType=N AttributeName=GSI_PK,AttributeType=S AttributeName=timestamp,AttributeType=N \
  --key-schema AttributeName=PK,KeyType=HASH AttributeName=SK,KeyType=RANGE \
  --global-secondary-indexes "[{\"IndexName\":\"GSI1\",\"KeySchema\":[{\"AttributeName\":\"category\",\"KeyType\":\"HASH\"},{\"AttributeName\":\"price\",\"KeyType\":\"RANGE\"}],\"Projection\":{\"ProjectionType\":\"ALL\"}},{\"IndexName\":\"GSI2\",\"KeySchema\":[{\"AttributeName\":\"GSI_PK\",\"KeyType\":\"HASH\"},{\"AttributeName\":\"timestamp\",\"KeyType\":\"RANGE\"}],\"Projection\":{\"ProjectionType\":\"ALL\"}}]" \
  --billing-mode PAY_PER_REQUEST

# Config & Secrets
awslocal ssm put-parameter --name "/marketplace/table_name" --type "String" --value "Products"
awslocal secretsmanager create-secret --name "LogisticsApiKey" --secret-string '{"api_key": "super-secret-key-123"}'
```

### 3. Build & Run Locally
```bash
mvn package
sam local start-api --region us-east-1
```

## 🧠 Technical Lessons Learned (Gotchas)
- **Networking:** On Linux, when using SAM inside a container, use the host IP (e.g., `192.168.x.x`) for `AWS_ENDPOINT_URL` to reach LocalStack, as `host.docker.internal` might not resolve.
- **TransactionWriteItems:** Used for orders to ensure that stock decrement and order creation happen atomically.
- **SSM in Lambda:** Fetching parameters during Lambda initialization (constructor) significantly reduces runtime overhead compared to fetching on every request.


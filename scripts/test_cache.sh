#!/bin/bash
API_URL="http://127.0.0.1:3001"
AUTH_HEADER="Authorization: Bearer allow-me"

echo "--- 1. Flushing Redis for a clean test ---"
docker exec marketplace-redis redis-cli flushall

echo -e "\n--- 2. First request (Cold - from DynamoDB) ---"
time curl -s -H "$AUTH_HEADER" "$API_URL/products" > /dev/null

echo -e "\n--- 3. Second request (Hot - from Redis Cache) ---"
echo "Should be much faster!"
time curl -s -H "$AUTH_HEADER" "$API_URL/products" > /dev/null

echo -e "\n--- 4. Adding a new product (Cache Invalidation) ---"
curl -s -X POST "$API_URL/products" \
  -H "$AUTH_HEADER" \
  -H "Content-Type: application/json" \
  -d '{"name": "Cache Tester", "price": 99.99, "category": "Test", "stockQuantity": 5}'

echo -e "\n\n--- 5. Third request after invalidation (Cold again) ---"
echo "Cache should be empty, so we hit DynamoDB again."
time curl -s -H "$AUTH_HEADER" "$API_URL/products" > /dev/null

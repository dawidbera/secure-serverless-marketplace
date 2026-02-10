#!/bin/bash
API_URL="http://127.0.0.1:3001"
AUTH_HEADER="Authorization: Bearer allow-me"

# Get the bucket name
BUCKET_NAME=$(awslocal s3 ls | awk '{print $3}' | grep marketplace-assets)

if [ -z "$BUCKET_NAME" ]; then
    echo "Error: S3 bucket not found in LocalStack!"
    exit 1
fi

echo "--- 1. Creating a test product and uploading a file to S3 ---"
# Create product to get an ID
PROD_JSON=$(curl -s -X POST "$API_URL/products" \
  -H "$AUTH_HEADER" \
  -H "Content-Type: application/json" \
  -d '{"name": "Asset Item", "price": 10.0, "category": "Digital"}')

PROD_ID=$(echo $PROD_JSON | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')

if [ -z "$PROD_ID" ]; then
    echo "Error: Failed to create product. Response: $PROD_JSON"
    exit 1
fi

echo "Created product with ID: $PROD_ID"

# Create a dummy file and upload it to the correct S3 location
echo "This is digital asset content" > item.zip
awslocal s3 cp item.zip "s3://$BUCKET_NAME/assets/$PROD_ID/item.zip"
rm item.zip

echo -e "\n--- 2. Fetching Pre-signed URL ---"
URL_JSON=$(curl -s -H "$AUTH_HEADER" "$API_URL/products/$PROD_ID/asset")
DOWNLOAD_URL=$(echo $URL_JSON | sed -n 's/.*"downloadUrl": "\([^"]*\)".*/\1/p')

if [ -z "$DOWNLOAD_URL" ]; then
    echo "Error: Failed to fetch download URL. Response: $URL_JSON"
    exit 1
fi

echo "Generated link: $DOWNLOAD_URL"

echo -e "\n--- 3. Attempting to download the file using the link ---"
curl -I "$DOWNLOAD_URL"

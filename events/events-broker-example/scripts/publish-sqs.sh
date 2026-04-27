#!/bin/bash
ENDPOINT_URL="http://localhost:4566"
REGION="us-east-1"
QUEUE_URL="http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/events-queue"

MESSAGE_BODY="${1:-'{\"event\":\"order.created\",\"orderId\":\"ORD-12345\",\"timestamp\":\"2026-03-31T12:00:00Z\"}'}"

echo "Publishing message to SQS queue..."
aws --endpoint-url="$ENDPOINT_URL" --region "$REGION" \
    sqs send-message \
    --queue-url "$QUEUE_URL" \
    --message-body "$MESSAGE_BODY"

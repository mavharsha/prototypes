#!/bin/bash
MESSAGE_BODY="${1:-'{\"event\":\"order.created\",\"orderId\":\"ORD-12345\",\"timestamp\":\"2026-03-31T12:00:00Z\"}'}"

echo "Publishing message to Artemis queue: events-queue"
docker exec "$(docker compose ps -q artemis)" \
    /var/lib/artemis-instance/bin/artemis producer \
    --destination "events-queue" \
    --message-count 1 \
    --message "$MESSAGE_BODY" \
    --user artemis \
    --password artemis
echo "Message published successfully"

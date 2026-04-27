#!/bin/bash
TOPIC="events-topic"
BROKER="localhost:9092"

MESSAGE_BODY="${1:-'{\"event\":\"order.created\",\"orderId\":\"ORD-12345\",\"timestamp\":\"2026-03-31T12:00:00Z\"}'}"

echo "Publishing message to Kafka topic: $TOPIC"
echo "$MESSAGE_BODY" | docker exec -i "$(docker compose ps -q kafka)" \
    kafka-console-producer.sh \
    --broker-list "$BROKER" \
    --topic "$TOPIC"
echo "Message published successfully"

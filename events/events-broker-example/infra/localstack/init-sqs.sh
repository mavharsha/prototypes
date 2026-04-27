#!/bin/bash
echo "Creating SQS queue: events-queue"
awslocal sqs create-queue --queue-name events-queue
echo "SQS queue created successfully"

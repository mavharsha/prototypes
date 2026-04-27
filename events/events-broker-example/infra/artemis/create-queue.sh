#!/bin/bash
echo "Creating Artemis queue: events-queue"
/var/lib/artemis-instance/bin/artemis queue create \
  --name events-queue \
  --address events-queue \
  --anycast \
  --no-durable \
  --auto-create-address \
  --user artemis \
  --password artemis \
  --silent
echo "Artemis queue created successfully"

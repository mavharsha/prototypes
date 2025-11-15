#!/bin/bash

echo "Starting OAuth2 Services..."
echo ""
echo "Services:"
echo "  - OAuth Server: http://localhost:4000"
echo "  - API Server: http://localhost:5000"
echo "  - Test Client: http://localhost:3001"
echo ""

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo "Error: Docker is not installed"
    exit 1
fi

# Check if Docker Compose is installed
if ! command -v docker-compose &> /dev/null; then
    echo "Error: Docker Compose is not installed"
    exit 1
fi

# Start services
echo "Building and starting services..."
docker-compose up --build

# Cleanup on exit
trap "docker-compose down" EXIT


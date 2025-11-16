# Use official Node.js runtime as base image
FROM node:18-alpine

# Set working directory in container
WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies
RUN npm install --production

# Copy application code
COPY index.js ./

# Create data directory for volume mounting
RUN mkdir -p /data

# Run the application
CMD ["npm", "start"]


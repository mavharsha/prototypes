# Docker Volume Example with Node.js

This example demonstrates Docker volumes for persistent data storage. A Node.js application generates fake events using the Faker library and writes them to a file every 15 seconds. The data persists even when the container is stopped or removed.

## 📋 What This Example Does

- Generates realistic fake events (logins, purchases, page views, etc.)
- Writes events to a log file every 15 seconds
- Uses Docker volumes to persist data across container restarts
- Demonstrates the difference between ephemeral and persistent storage

## 🚀 Quick Start

### Option 1: Using Docker Compose (Recommended)

```bash
# Start the service
docker-compose up -d

# View logs
docker-compose logs -f

# Stop the service
docker-compose down
```

### Option 2: Using Docker CLI

```bash
# Build the image
docker build -t event-logger .

# Run with a named volume
docker run -d --name event-logger -v event-data:/data event-logger

# View logs
docker logs -f event-logger

# Stop and remove container
docker stop event-logger
docker rm event-logger
```

## 📊 Viewing the Event Data

The events are stored in `/data/events.log` inside the container. Here are several ways to view them:

### Method 1: Using docker exec

```bash
# View the entire log file
docker exec event-logger cat /data/events.log

# Tail the last 10 events
docker exec event-logger tail -10 /data/events.log

# Follow new events in real-time
docker exec event-logger tail -f /data/events.log
```

### Method 2: Copy file to host

```bash
# Copy the log file to your current directory
docker cp event-logger:/data/events.log ./events.log

# View with any text editor or command
cat events.log | jq .  # Pretty print JSON (if jq is installed)
```

### Method 3: Inspect the volume directly

```bash
# List all volumes
docker volume ls

# Inspect the volume
docker volume inspect event-data

# On Windows, you can access Docker volumes through Docker Desktop
# or by running a temporary container:
docker run --rm -v event-data:/data alpine cat /data/events.log
```

## 🔄 Testing Data Persistence

To verify that data persists across container restarts:

```bash
# 1. Start the container
docker-compose up -d

# 2. Wait 30-60 seconds, then check the log
docker exec event-logger cat /data/events.log

# 3. Stop and remove the container
docker-compose down

# 4. Start a new container (data should still be there!)
docker-compose up -d

# 5. Check the log again - previous events should still exist!
docker exec event-logger cat /data/events.log
```

## 📁 Project Structure

```
docker-volume/
├── index.js              # Node.js application
├── package.json          # Dependencies (faker)
├── Dockerfile           # Container image definition
├── docker-compose.yml   # Service and volume configuration
└── README.md           # This file
```

## 🔍 Understanding the Volume

### Named Volume (Used in this example)
- Managed by Docker
- Location: Docker's internal storage
- Persists data across container restarts
- Shared between containers if needed
- Best for production use

```yaml
volumes:
  - event-data:/data
```

### Alternative: Bind Mount
You can also use a bind mount to store data on your host machine:

```bash
# Windows PowerShell
docker run -d --name event-logger -v ${PWD}/data:/data event-logger

# Linux/Mac
docker run -d --name event-logger -v $(pwd)/data:/data event-logger
```

Or in docker-compose.yml:
```yaml
volumes:
  - ./data:/data  # Bind mount to local directory
```

## 🛠️ Development

### Install dependencies locally (optional)
```bash
npm install
```

### Run locally without Docker
```bash
node index.js
# Events will be written to /data/events.log (or ./data/ if you modify the code)
```

## 🧹 Cleanup

### Remove container and keep data
```bash
docker-compose down
# Volume persists, data is safe
```

### Remove everything including data
```bash
docker-compose down -v
# This removes the volume and all event data
```

### Manual volume cleanup
```bash
# List volumes
docker volume ls

# Remove specific volume
docker volume rm event-data

# Remove all unused volumes
docker volume prune
```

## 📝 Event Format

Each event is a JSON object with the following structure:

```json
{
  "timestamp": "2025-11-16T10:30:45.123Z",
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "purchase",
  "user": {
    "id": "7f9a8b6c-4d3e-2f1a-0b9c-8d7e6f5a4b3c",
    "name": "John Doe",
    "email": "john.doe@example.com",
    "ip": "192.168.1.100"
  },
  "metadata": {
    "userAgent": "Mozilla/5.0...",
    "location": "New York, United States",
    "device": "desktop",
    "amount": 259.99
  }
}
```

## 💡 Key Concepts Demonstrated

1. **Volume Persistence**: Data survives container restarts
2. **Volume Isolation**: Each container can have its own volume
3. **Volume Sharing**: Multiple containers can share the same volume
4. **Volume Management**: Docker manages the volume lifecycle

## 🔧 Technical Implementation: Volume Configuration Details

This section documents all the configurations and code changes made to support Docker volumes in this application.

### 1. Docker Compose Volume Configuration

**File: `docker-compose.yml` (Lines 7-17)**

```yaml
services:
  event-logger:
    volumes:
      # Named volume - data persists even after container is removed
      - event-data:/data
    
volumes:
  # Named volume for persistent storage
  event-data:
    driver: local
```

**Configuration Details:**

- **Named Volume**: `event-data` is a Docker-managed volume that persists independently of container lifecycle
- **Mount Point**: The volume is mounted at `/data` inside the container
- **Driver**: Uses the `local` driver (default), which stores data on the host's filesystem in Docker's storage directory
- **Persistence**: Data persists even when the container is removed with `docker-compose down` (but not with `docker-compose down -v`)

**Alternative Volume Types:**

```yaml
# Bind mount (for development)
volumes:
  - ./data:/data

# Anonymous volume (temporary)
volumes:
  - /data

# Named volume with custom driver options
volumes:
  event-data:
    driver: local
    driver_opts:
      type: none
      device: /custom/path
      o: bind
```

### 2. Dockerfile Volume Setup

**File: `Dockerfile` (Lines 16-17)**

```dockerfile
# Create data directory for volume mounting
RUN mkdir -p /data
```

**Purpose:**
- Creates the `/data` directory inside the image
- Ensures the mount point exists before the volume is mounted
- Even though Docker creates missing directories automatically, explicitly creating it:
  - Makes the volume mount point visible in the image
  - Provides better documentation
  - Allows setting specific permissions if needed (e.g., `RUN mkdir -p /data && chown node:node /data`)

**Volume Mount Behavior:**
- When the container starts, Docker mounts the `event-data` volume over the `/data` directory
- Any files in the image's `/data` directory are copied to the volume on first mount (if volume is empty)
- Subsequent mounts use the volume's existing data

### 3. Application Code Configuration

**File: `index.js` (Lines 5-12)**

```javascript
// Directory for persistent data
const DATA_DIR = '/data';
const LOG_FILE = path.join(DATA_DIR, 'events.log');

// Ensure data directory exists
if (!fs.existsSync(DATA_DIR)) {
  fs.mkdirSync(DATA_DIR, { recursive: true });
}
```

**Configuration Details:**

- **Hardcoded Path**: Uses `/data` as the persistent storage location (matches the volume mount point)
- **Directory Check**: Verifies the directory exists (defensive programming for non-Docker environments)
- **File Path**: All events are written to `/data/events.log`
- **Append Mode**: Uses `fs.appendFile()` to add new events without overwriting existing data

**Why `/data`?**
- Common convention for data directories in containers
- Separates application code (`/app`) from persistent data (`/data`)
- Makes volume mounts clear and explicit

**Alternative Approaches:**

```javascript
// Using environment variable for flexibility
const DATA_DIR = process.env.DATA_DIR || '/data';

// Using different file per container instance
const LOG_FILE = path.join(DATA_DIR, `events-${process.env.HOSTNAME}.log`);

// Rotating log files by date
const LOG_FILE = path.join(DATA_DIR, `events-${new Date().toISOString().split('T')[0]}.log`);
```

### 4. Volume Lifecycle Management

**Creation:**
```bash
# Volume is automatically created on first `docker-compose up`
docker-compose up -d

# Or manually create before running
docker volume create event-data
```

**Inspection:**
```bash
# View volume details including mount point on host
docker volume inspect event-data

# Output shows:
# "Mountpoint": "/var/lib/docker/volumes/event-data/_data" (Linux)
# Or Docker Desktop's virtual machine path (Windows/Mac)
```

**Backup:**
```bash
# Backup volume to tar archive
docker run --rm -v event-data:/data -v $(pwd):/backup alpine tar czf /backup/event-data-backup.tar.gz -C /data .

# Restore from backup
docker run --rm -v event-data:/data -v $(pwd):/backup alpine tar xzf /backup/event-data-backup.tar.gz -C /data
```

**Cleanup:**
```bash
# Remove volume with data
docker volume rm event-data

# Or with docker-compose
docker-compose down -v  # Removes volumes defined in compose file
```

### 5. Volume Driver Options

**Current Configuration: Local Driver**

The `local` driver is Docker's default and stores data on the host filesystem.

**Location by Platform:**
- **Linux**: `/var/lib/docker/volumes/<volume-name>/_data`
- **Windows (Docker Desktop)**: Inside the Docker Desktop VM
- **Mac (Docker Desktop)**: Inside the Docker Desktop VM

**Advanced Driver Options:**

```yaml
# NFS network volume
volumes:
  event-data:
    driver: local
    driver_opts:
      type: nfs
      o: addr=192.168.1.100,rw
      device: ":/path/to/dir"

# Azure File Share
volumes:
  event-data:
    driver: azure_file
    driver_opts:
      share_name: myshare
      storage_account_name: mystorageaccount

# AWS EFS (using third-party plugin)
volumes:
  event-data:
    driver: rexray/efs
    driver_opts:
      volumeType: gp2
```

### 6. Environment Variables for Configuration

**File: `docker-compose.yml` (Lines 11-12)**

```yaml
environment:
  - NODE_ENV=production
```

**Potential Volume-Related Environment Variables:**

```yaml
environment:
  - NODE_ENV=production
  - DATA_DIR=/data              # Where to store data
  - LOG_FILE=events.log         # Log filename
  - WRITE_INTERVAL=15000        # Milliseconds between writes
  - MAX_FILE_SIZE=100MB         # Log rotation size
```

These could be consumed in `index.js`:

```javascript
const DATA_DIR = process.env.DATA_DIR || '/data';
const LOG_FILE = path.join(DATA_DIR, process.env.LOG_FILE || 'events.log');
const WRITE_INTERVAL = parseInt(process.env.WRITE_INTERVAL) || 15000;
```

### 7. Performance Considerations

**Write Performance:**
- **Buffering**: Current implementation uses `fs.appendFile()` which buffers writes
- **Sync vs Async**: Using async writes prevents blocking the event loop
- **Write Frequency**: 15-second interval balances data freshness with I/O overhead

**Volume Performance Tips:**
- Named volumes are generally faster than bind mounts
- For high-throughput logging, consider using a logging driver instead of file writes
- On Windows/Mac, Docker Desktop adds VM overhead compared to Linux

### 8. Data Persistence Verification

**Testing Persistence:**

```bash
# 1. Start container and write some events
docker-compose up -d
sleep 30

# 2. Check event count
docker exec event-logger sh -c "wc -l /data/events.log"
# Output: 3 /data/events.log

# 3. Stop and remove container
docker-compose down

# 4. Start new container
docker-compose up -d

# 5. Check event count (should include old events)
docker exec event-logger sh -c "wc -l /data/events.log"
# Output: 5 /data/events.log (kept old 3 + added 2 new)
```

### 9. Multi-Container Volume Sharing

To share the volume between multiple containers:

```yaml
version: '3.8'

services:
  event-logger:
    build: .
    volumes:
      - event-data:/data
  
  event-reader:
    image: alpine
    volumes:
      - event-data:/data:ro  # Mount as read-only
    command: tail -f /data/events.log

volumes:
  event-data:
    driver: local
```

**Use Cases:**
- Log aggregation
- Shared configuration
- Data processing pipelines

### 10. Security Considerations

**Read-Only Mounts:**
```yaml
volumes:
  - event-data:/data:ro  # Read-only
```

**User Permissions:**
```dockerfile
# Run as non-root user
RUN addgroup -g 1001 appuser && adduser -D -u 1001 -G appuser appuser
RUN mkdir -p /data && chown appuser:appuser /data
USER appuser
```

**Volume Encryption:**
- Use encrypted storage on host
- Use volume plugins with encryption support
- Encrypt data at application level before writing

### 11. Troubleshooting Volume Issues

**Volume Not Persisting:**
```bash
# Check if volume exists
docker volume ls | grep event-data

# Check volume mount in container
docker exec event-logger mount | grep /data

# Verify volume is defined in docker-compose.yml
docker-compose config
```

**Permission Denied Errors:**
```bash
# Check directory permissions
docker exec event-logger ls -la /data

# Fix permissions (if running as non-root)
docker exec -u root event-logger chown -R node:node /data
```

**Volume Full:**
```bash
# Check volume size (on Linux)
docker exec event-logger df -h /data

# Check log file size
docker exec event-logger du -h /data/events.log
```

### 12. Summary of Volume Configuration

| Component | Configuration | Purpose |
|-----------|--------------|---------|
| **docker-compose.yml** | `volumes: event-data:/data` | Mounts named volume to container |
| **docker-compose.yml** | `volumes: event-data: driver: local` | Defines named volume with local driver |
| **Dockerfile** | `RUN mkdir -p /data` | Creates mount point directory |
| **index.js** | `const DATA_DIR = '/data'` | Application uses volume path |
| **index.js** | `fs.appendFile(LOG_FILE, ...)` | Writes data to persistent location |

**Data Flow:**
1. Application writes to `/data/events.log` inside container
2. Docker redirects writes to volume `event-data`
3. Volume stores data in host filesystem (managed by Docker)
4. Data persists when container stops/restarts
5. New container reuses same volume and sees existing data

## 🎓 Learning Resources

- [Docker Volumes Documentation](https://docs.docker.com/storage/volumes/)
- [Faker.js Documentation](https://fakerjs.dev/)
- [Node.js File System](https://nodejs.org/api/fs.html)

## 🤝 Common Use Cases for Volumes

- **Database storage**: Persist database files
- **Application logs**: Store logs for analysis
- **User uploads**: Store uploaded files
- **Configuration**: Share config files between containers
- **Backups**: Easy backup and restore of data


# Setup Guide

## Quick Start with Docker Compose

### Prerequisites
- Docker
- Docker Compose

### Start All Services

**Windows (PowerShell):**
```powershell
.\start-services.ps1
```

**Linux/Mac:**
```bash
chmod +x start-services.sh
./start-services.sh
```

**Or manually:**
```bash
docker-compose up --build
```

### Access the Application

Once all services are running:

1. **Test Client**: http://localhost:3001
2. **OAuth Server**: http://localhost:4000
3. **API Server**: http://localhost:5000

### Stop Services

Press `Ctrl+C` in the terminal where docker-compose is running, then:

```bash
docker-compose down
```

## Manual Setup (Without Docker)

### Prerequisites
- Node.js 16+ 
- npm

### Installation

1. **Install OAuth Server dependencies:**
```bash
cd oauth-server
npm install
```

2. **Install API Server dependencies:**
```bash
cd ../api-server
npm install
```

3. **Install Test Client dependencies:**
```bash
cd ..
npm install
```

### Start Services Manually

You'll need 3 separate terminal windows:

**Terminal 1 - OAuth Server:**
```bash
cd oauth-server
npm start
```

**Terminal 2 - API Server:**
```bash
cd api-server
npm start
```

**Terminal 3 - Test Client:**
```bash
npm run client
```

## Testing the OAuth Flow

1. Open browser to http://localhost:3001
2. Click "Authorize with OAuth2 Server"
3. You'll be automatically authorized (in production, there would be a consent screen)
4. After redirect, you'll have access and refresh tokens
5. Click "Access Protected Resource" to test API access
6. Try "Refresh Access Token" to renew the token

## API Testing with cURL

### 1. Get Authorization Code

Open in browser:
```
http://localhost:4000/oauth/authorize?client_id=test-client&redirect_uri=http://localhost:3001/callback&response_type=code&scope=read+write&state=test123
```

Copy the `code` parameter from the redirect URL.

### 2. Exchange Code for Token

```bash
curl -X POST http://localhost:4000/oauth/token \
  -H "Content-Type: application/json" \
  -d '{
    "grant_type": "authorization_code",
    "code": "YOUR_CODE_HERE",
    "redirect_uri": "http://localhost:3001/callback",
    "client_id": "test-client",
    "client_secret": "test-secret"
  }'
```

### 3. Access Protected Resource

```bash
curl http://localhost:5000/api/protected \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

### 4. Refresh Token

```bash
curl -X POST http://localhost:4000/oauth/token \
  -H "Content-Type: application/json" \
  -d '{
    "grant_type": "refresh_token",
    "refresh_token": "YOUR_REFRESH_TOKEN",
    "client_id": "test-client",
    "client_secret": "test-secret"
  }'
```

## Health Checks

Check if services are running:

```bash
# OAuth Server
curl http://localhost:4000/health

# API Server
curl http://localhost:5000/health
```

## Troubleshooting

### Port Already in Use

If you get port conflict errors, you can change the ports in `docker-compose.yml`:

```yaml
services:
  oauth-server:
    ports:
      - "4000:4000"  # Change first number to different port
```

### Services Not Connecting

Make sure all services are running and healthy:

```bash
docker-compose ps
```

### View Logs

```bash
# All services
docker-compose logs

# Specific service
docker-compose logs oauth-server
docker-compose logs api-server
docker-compose logs test-client
```

### Reset Everything

```bash
docker-compose down -v
docker-compose up --build
```

## Architecture

The application consists of three separate services:

1. **OAuth Server** (Port 4000)
   - Handles authorization requests
   - Issues access and refresh tokens
   - Provides token introspection

2. **API Server** (Port 5000)
   - Hosts protected API endpoints
   - Validates tokens with OAuth server
   - Enforces scope-based authorization

3. **Test Client** (Port 3001)
   - Demo web application
   - Implements OAuth2 authorization code flow
   - Demonstrates token usage and refresh

## Next Steps

- Explore the different API endpoints in `api-server/server.js`
- Try modifying scopes in `oauth-server/server.js`
- Implement additional OAuth2 flows (implicit, client credentials)
- Add a database for persistent storage
- Implement proper user authentication


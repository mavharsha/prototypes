# Quick Reference

## 🚀 Start Services

### Docker (Recommended)
```bash
docker-compose up --build
```

### Windows
```powershell
.\start-services.ps1
```

### Linux/Mac
```bash
chmod +x start-services.sh
./start-services.sh
```

## 🌐 Service URLs

| Service | URL | Purpose |
|---------|-----|---------|
| Test Client | http://localhost:3001 | Interactive OAuth demo |
| OAuth Server | http://localhost:4000 | Authorization & tokens |
| API Server | http://localhost:5000 | Protected resources |

## 🔑 Test Credentials

```javascript
client_id: "test-client"
client_secret: "test-secret"
redirect_uri: "http://localhost:3001/callback"
scopes: ["read", "write"]
```

## 📡 Key Endpoints

### OAuth Server (4000)
```
GET  /oauth/authorize      - Start OAuth flow
POST /oauth/token          - Get/refresh tokens
POST /oauth/introspect     - Validate tokens
GET  /health               - Health check
```

### API Server (5000)
```
GET  /api/protected        - Basic protected resource
GET  /api/user/profile     - User profile (read scope)
POST /api/user/update      - Update user (write scope)
GET  /api/data             - Get data
GET  /health               - Health check
```

## 🧪 Testing with cURL

### 1. Get Authorization Code
```bash
# Open in browser:
http://localhost:4000/oauth/authorize?client_id=test-client&redirect_uri=http://localhost:3001/callback&response_type=code&scope=read+write&state=test123

# Copy the 'code' from redirect URL
```

### 2. Exchange for Token
```bash
curl -X POST http://localhost:4000/oauth/token \
  -H "Content-Type: application/json" \
  -d '{
    "grant_type": "authorization_code",
    "code": "YOUR_CODE",
    "redirect_uri": "http://localhost:3001/callback",
    "client_id": "test-client",
    "client_secret": "test-secret"
  }'
```

### 3. Call Protected API
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

## 🐳 Docker Commands

```bash
# Start services
docker-compose up -d

# View logs
docker-compose logs -f

# View specific service logs
docker-compose logs -f oauth-server
docker-compose logs -f api-server

# Check status
docker-compose ps

# Stop services
docker-compose down

# Rebuild and restart
docker-compose up --build

# Scale API server
docker-compose up --scale api-server=3
```

## 🔧 NPM Scripts

```bash
# Start test client only
npm run client

# Start legacy monolithic server
npm run legacy

# Docker shortcuts
npm run docker:up
npm run docker:down
npm run docker:logs
```

## 🏗️ Manual Service Startup

```bash
# Terminal 1 - OAuth Server
cd oauth-server
npm install
npm start

# Terminal 2 - API Server
cd api-server
npm install
npm start

# Terminal 3 - Test Client
npm install
npm run client
```

## 📊 Health Checks

```bash
# Check all services
curl http://localhost:4000/health
curl http://localhost:5000/health

# Expected responses
{
  "status": "OK",
  "service": "oauth-server",
  "timestamp": "2025-11-15T...",
  "active_tokens": 0,
  "active_codes": 0
}
```

## 🔍 Debugging

### View service logs
```bash
docker-compose logs oauth-server
docker-compose logs api-server
docker-compose logs test-client
```

### Enter container
```bash
docker exec -it oauth-server sh
docker exec -it api-server sh
```

### Check network connectivity
```bash
docker network inspect oauth2_oauth-network
```

## 🛑 Stop Everything

```bash
# Stop containers
docker-compose down

# Stop and remove volumes
docker-compose down -v

# Stop and remove everything
docker-compose down -v --rmi all
```

## 📝 Common Environment Variables

```bash
# OAuth Server
PORT=4000
NODE_ENV=production

# API Server
PORT=5000
OAUTH_SERVER=http://oauth-server:4000
NODE_ENV=production

# Test Client
PORT=3001
OAUTH_SERVER=http://localhost:4000
API_SERVER=http://localhost:5000
```

## 🎯 OAuth Flow Summary

```
1. User → Authorize → OAuth Server
2. OAuth Server → Code → User
3. Client → Exchange Code → OAuth Server
4. OAuth Server → Tokens → Client
5. Client → API + Token → API Server
6. API Server → Validate Token → OAuth Server
7. OAuth Server → Token Info → API Server
8. API Server → Resource → Client
```

## 📚 Documentation

- [README.md](README.md) - Overview
- [SETUP.md](SETUP.md) - Detailed setup
- [ARCHITECTURE.md](ARCHITECTURE.md) - System design
- [MIGRATION.md](MIGRATION.md) - Migration guide

## 🆘 Troubleshooting

### Port already in use
```bash
# Find process
netstat -ano | findstr :4000
# Kill it (Windows)
taskkill /PID <PID> /F
```

### Services can't connect
```bash
# Check if running
docker-compose ps

# Restart
docker-compose restart
```

### Token validation fails
```bash
# Check OAuth server is accessible
curl http://localhost:4000/health

# Check API server config
docker-compose logs api-server | grep OAUTH_SERVER
```

## 💡 Tips

- Use Docker Compose for easiest setup
- Access test client UI for interactive testing
- Check health endpoints to verify services
- View logs for debugging
- Use curl for API testing
- Read ARCHITECTURE.md to understand flow


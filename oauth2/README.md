# OAuth2 Server with Protected APIs (only for learning purpose)

A complete OAuth2 implementation with **separated microservices architecture**:
- **OAuth Server** - Authorization and token management (Port 4000)
- **API Server** - Protected API endpoints (Port 5000)
- **Test Client** - Interactive demo application (Port 3001)

> 📦 Each service runs independently and can be deployed separately or together using Docker Compose.

## ⚡ Quick Start

### Option 1: Docker Compose (Recommended)

**Make sure Docker Desktop is running**, then:

```bash
docker-compose up --build
```

**Access the services:**
- 🎯 **Test Client:** http://localhost:3001 ← Start here!
- 🔐 **OAuth Server:** http://localhost:4000
- 🛡️ **API Server:** http://localhost:5000

**Stop services:**
```bash
docker-compose down
```

### Option 2: Quick Start Scripts

**Windows (PowerShell):**
```powershell
.\start-services.ps1
```

**Linux/Mac:**
```bash
chmod +x start-services.sh
./start-services.sh
```

### Option 3: Manual Setup (Without Docker)

**Prerequisites:** Node.js 16+ and npm

**Terminal 1 - OAuth Server:**
```bash
cd oauth-server
npm install
npm start
```

**Terminal 2 - API Server:**
```bash
cd api-server
npm install
npm start
```

**Terminal 3 - Test Client:**
```bash
npm install
npm run client
```

## 🎮 Try the OAuth Flow

1. Open http://localhost:3001
2. Click "Authorize with OAuth2 Server"
3. Get tokens automatically (via redirect)
4. Click "Access Protected Resource"
5. See your protected data!

## 📐 Architecture

```
┌─────────────┐         ┌──────────────┐         ┌─────────────┐
│             │         │              │         │             │
│ Test Client │◄────────┤ OAuth Server │────────►│ API Server  │
│             │         │              │         │             │
│  Port 3001  │         │  Port 4000   │         │  Port 5000  │
└─────────────┘         └──────────────┘         └─────────────┘
      │                        │                        │
      └────────── OAuth Flow ──┘                        │
      └────────────────── Protected API Calls ──────────┘
```

**Flow:**
1. Client → OAuth Server (authorize)
2. OAuth Server → Client (code)
3. Client → OAuth Server (exchange code)
4. OAuth Server → Client (access + refresh tokens)
5. Client → API Server (request with token)
6. API Server → OAuth Server (validate token)
7. OAuth Server → API Server (token info)
8. API Server → Client (protected data)

For detailed architecture, see [ARCHITECTURE.md](ARCHITECTURE.md).

## 📚 Project Structure

```
oauth2/
├── oauth-server/              # OAuth Authorization Server
│   ├── server.js              # OAuth endpoints
│   ├── package.json           # Dependencies
│   └── Dockerfile             # Container config
│
├── api-server/                # Protected API Server
│   ├── server.js              # Business logic & APIs
│   ├── package.json           # Dependencies
│   └── Dockerfile             # Container config
│
├── test-client.js             # Demo OAuth client
├── docker-compose.yml         # Multi-service orchestration
├── start-services.sh          # Quick start (Linux/Mac)
├── start-services.ps1         # Quick start (Windows)
│
└── server.js                  # Legacy monolithic version
```

## 🔌 API Endpoints

### OAuth Server (Port 4000)
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/oauth/authorize` | GET | Start OAuth flow |
| `/oauth/token` | POST | Get/refresh tokens |
| `/oauth/introspect` | POST | Validate tokens |
| `/health` | GET | Health check |

### API Server (Port 5000)
| Endpoint | Method | Description | Scope Required |
|----------|--------|-------------|----------------|
| `/api/protected` | GET | Basic protected resource | Any |
| `/api/user/profile` | GET | User profile | read |
| `/api/user/update` | POST | Update user | write |
| `/api/data` | GET | Data access | Any |
| `/health` | GET | Health check | None |

## 🔑 Test Credentials

```javascript
Client ID:      test-client
Client Secret:  test-secret
Redirect URI:   http://localhost:3001/callback
Scopes:         read, write
```

## 🧪 Quick Reference

### Start Services
```bash
# Using Docker Compose
docker-compose up --build

# View logs
docker-compose logs -f

# Stop services
docker-compose down
```

### Test with cURL

**1. Get authorization code (open in browser):**
```
http://localhost:4000/oauth/authorize?client_id=test-client&redirect_uri=http://localhost:3001/callback&response_type=code&scope=read+write
```

**2. Exchange code for token:**
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

**3. Call protected API:**
```bash
curl http://localhost:5000/api/protected \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

**4. Refresh token:**
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

### Docker Commands
```bash
# Start in background
docker-compose up -d

# View specific service logs
docker-compose logs -f oauth-server
docker-compose logs -f api-server

# Check status
docker-compose ps

# Restart services
docker-compose restart

# Clean up everything
docker-compose down -v
```

### Health Checks
```bash
curl http://localhost:4000/health
curl http://localhost:5000/health
```

## 🛠️ Development

### Running Services Manually

**Terminal 1 - OAuth Server:**
```bash
cd oauth-server
npm install
npm start
```

**Terminal 2 - API Server:**
```bash
cd api-server
npm install
npm start
```

**Terminal 3 - Test Client:**
```bash
npm install
npm run client
```

### Environment Variables

**OAuth Server:**
- `PORT` - Server port (default: 4000)
- `NODE_ENV` - Environment (development/production)

**API Server:**
- `PORT` - Server port (default: 5000)
- `OAUTH_SERVER` - OAuth server URL (default: http://localhost:4000)
- `NODE_ENV` - Environment (development/production)

**Test Client:**
- `PORT` - Client port (default: 3001)
- `OAUTH_SERVER` - OAuth server URL (default: http://localhost:4000)
- `API_SERVER` - API server URL (default: http://localhost:5000)

## ✨ Features

✅ Complete OAuth2 Authorization Code Flow  
✅ Token Refresh Mechanism  
✅ Token Introspection  
✅ Scope-based Authorization  
✅ Microservices Architecture  
✅ Docker Compose Support  
✅ Health Check Endpoints  
✅ CORS Enabled  
✅ Interactive Test Client  

## 🔒 Security Notes

⚠️ **This is a demo implementation for learning purposes.**

**For production, you should:**
- Use a proper database for token storage
- Implement proper user authentication
- Use HTTPS for all communications
- Implement rate limiting
- Add proper logging and monitoring
- Use environment variables for secrets
- Implement PKCE for public clients
- Add CSRF protection
- Implement proper session management

## 🆘 Troubleshooting

### Docker Desktop Not Running
```
Error: unable to get image 'oauth2-api-server'
```
**Solution:** Start Docker Desktop and wait for it to fully start.

### Port Already in Use
```bash
# Windows - Find and kill process
netstat -ano | findstr :4000
taskkill /PID <PID> /F

# Linux/Mac
lsof -ti:4000 | xargs kill
```

### Services Can't Connect
```bash
# Check if all services are running
docker-compose ps

# View logs
docker-compose logs

# Restart
docker-compose restart
```

### Token Validation Fails
- Ensure OAuth server is running: `curl http://localhost:4000/health`
- Check API server logs: `docker-compose logs api-server`
- Verify OAUTH_SERVER environment variable

## 📖 Additional Documentation

- **[ARCHITECTURE.md](ARCHITECTURE.md)** - Detailed system architecture and technical deep-dive

## 📦 What's Included

This implementation demonstrates:
- OAuth2 Authorization Code Flow
- Token refresh mechanism
- Token introspection for validation
- Scope-based authorization
- Microservices architecture
- Service-to-service communication
- Docker containerization
- Health checks and monitoring

## 🎓 Learning Resources

After getting it running:
1. Explore the test client UI to understand the OAuth flow
2. Read [ARCHITECTURE.md](ARCHITECTURE.md) for system design details
3. Check the source code in `oauth-server/` and `api-server/`
4. Experiment with different scopes and endpoints
5. Try adding new API endpoints or OAuth clients

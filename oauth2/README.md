# OAuth2 Server with Protected APIs

This project demonstrates a complete OAuth2 implementation with **separated microservices architecture**:
- **OAuth Server**: Handles authorization and token management (Port 4000)
- **API Server**: Provides protected API endpoints (Port 5000)
- **Test Client**: Demo client application to test the OAuth flow (Port 3001)

> 📦 Each service runs independently and can be deployed separately or together using Docker Compose.

## Architecture

```
┌─────────────┐         ┌──────────────┐         ┌─────────────┐
│             │         │              │         │             │
│ Test Client │◄────────┤ OAuth Server │────────►│ API Server  │
│             │         │              │         │             │
│  Port 3001  │         │  Port 4000   │         │  Port 5000  │
│             │         │              │         │             │
└─────────────┘         └──────────────┘         └─────────────┘
      │                        │                        │
      │                        │                        │
      └────────── OAuth Flow ──┘                        │
      └────────────────── Protected API Calls ──────────┘
```

## Services

### OAuth Server (Port 4000)
- `/oauth/authorize` - Authorization endpoint
- `/oauth/token` - Token issuance endpoint
- `/oauth/introspect` - Token introspection endpoint
- `/health` - Health check

### API Server (Port 5000)
- `/api/protected` - Basic protected resource
- `/api/user/profile` - User profile (requires read scope)
- `/api/user/update` - Update user (requires write scope)
- `/api/data` - Get data (requires auth)
- `/health` - Health check

### Test Client (Port 3001)
- Web interface to test OAuth2 flow
- Demonstrates authorization code grant
- Shows token refresh mechanism

## Quick Start

### 🐳 Using Docker Compose (Recommended)

**Start all services with one command:**

On Windows (PowerShell):
```powershell
.\start-services.ps1
```

On Linux/Mac:
```bash
chmod +x start-services.sh
./start-services.sh
```

Or directly:
```bash
docker-compose up --build
```

**Access the services:**
- Test Client UI: http://localhost:3001
- OAuth Server: http://localhost:4000
- API Server: http://localhost:5000

**Stop services:**
```bash
docker-compose down
```

For detailed setup instructions, see [SETUP.md](SETUP.md)

### Manual Setup

#### 1. Install Dependencies

For each service, install dependencies:

```bash
# OAuth Server
cd oauth-server
npm install

# API Server
cd ../api-server
npm install

# Test Client (in root)
cd ..
npm install
```

#### 2. Start Services

Start each service in a separate terminal:

```bash
# Terminal 1 - OAuth Server
cd oauth-server
npm start

# Terminal 2 - API Server
cd api-server
npm start

# Terminal 3 - Test Client
npm run client
```

## Testing the OAuth Flow

1. Open your browser to `http://localhost:3001`
2. Click "Authorize with OAuth2 Server"
3. You'll be redirected to the OAuth server and back
4. Once authorized, you'll have access and refresh tokens
5. Click "Access Protected Resource" to call the API server
6. The API server validates the token with the OAuth server
7. Try "Refresh Access Token" to get a new token

## Environment Variables

### OAuth Server
- `PORT` - Server port (default: 4000)
- `NODE_ENV` - Environment (development/production)

### API Server
- `PORT` - Server port (default: 5000)
- `OAUTH_SERVER` - OAuth server URL (default: http://localhost:4000)
- `NODE_ENV` - Environment (development/production)

### Test Client
- `PORT` - Client port (default: 3001)
- `OAUTH_SERVER` - OAuth server URL (default: http://localhost:4000)
- `API_SERVER` - API server URL (default: http://localhost:5000)

## OAuth2 Flow

1. **Authorization Request**: Client redirects user to OAuth server
2. **User Consent**: User authorizes the client (auto-approved in this demo)
3. **Authorization Code**: OAuth server redirects back with code
4. **Token Exchange**: Client exchanges code for access/refresh tokens
5. **API Access**: Client uses access token to call protected APIs
6. **Token Validation**: API server validates token with OAuth server
7. **Token Refresh**: Client can refresh expired tokens

## Client Credentials

Default test client:
- **Client ID**: `test-client`
- **Client Secret**: `test-secret`
- **Redirect URI**: `http://localhost:3001/callback`
- **Scopes**: `read`, `write`

## Security Notes

⚠️ **This is a demo implementation for learning purposes.**

In production, you should:
- Use a proper database for token storage
- Implement proper user authentication
- Use HTTPS for all communications
- Implement rate limiting
- Add proper logging and monitoring
- Use environment variables for secrets
- Implement PKCE for public clients
- Add CSRF protection
- Implement proper session management

## Documentation

- 🚀 [GET_STARTED.md](GET_STARTED.md) - **Start here!** Quick 2-minute guide
- 📘 [SETUP.md](SETUP.md) - Detailed setup and installation guide
- 🏗️ [ARCHITECTURE.md](ARCHITECTURE.md) - System architecture and design
- ⚡ [QUICK_REFERENCE.md](QUICK_REFERENCE.md) - Quick commands and API reference
- 🔄 [MIGRATION.md](MIGRATION.md) - Migration guide from monolithic version
- 📋 [CHANGES.md](CHANGES.md) - Summary of all changes made
- 📝 [OLD_SERVER_NOTE.md](OLD_SERVER_NOTE.md) - Note about legacy server.js

## Project Structure

```
oauth2/
├── oauth-server/              # OAuth Authorization Server
│   ├── server.js              # OAuth endpoints implementation
│   ├── package.json           # Dependencies
│   └── Dockerfile             # Container configuration
│
├── api-server/                # Protected API Server
│   ├── server.js              # Protected API endpoints
│   ├── package.json           # Dependencies
│   └── Dockerfile             # Container configuration
│
├── test-client.js             # Demo OAuth client application
├── docker-compose.yml         # Multi-container orchestration
├── start-services.sh          # Quick start script (Linux/Mac)
├── start-services.ps1         # Quick start script (Windows)
│
└── server.js                  # Legacy monolithic version (deprecated)
```

## Features

✅ Complete OAuth2 Authorization Code Flow  
✅ Token Refresh Mechanism  
✅ Token Introspection  
✅ Scope-based Authorization  
✅ Microservices Architecture  
✅ Docker Compose Support  
✅ Health Check Endpoints  
✅ CORS Enabled  
✅ Interactive Test Client  

## Contributing

This is a learning/demo project. Feel free to:
- Fork and experiment
- Report issues
- Suggest improvements
- Add new OAuth2 flows

## License

MIT

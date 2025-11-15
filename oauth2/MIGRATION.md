# Migration Guide

## From Monolithic to Microservices

This document explains the changes from the original monolithic implementation to the new microservices architecture.

## What Changed?

### Before (Monolithic)
```
oauth2/
  └── server.js (OAuth + Protected APIs in one file)
  └── test-client.js
  └── package.json
```

**Single Service** running on port 3000:
- OAuth endpoints (`/oauth/*`)
- Protected API endpoints (`/api/*`)
- All in-memory storage shared

### After (Microservices)

```
oauth2/
  ├── oauth-server/
  │   ├── server.js (OAuth only)
  │   ├── package.json
  │   └── Dockerfile
  ├── api-server/
  │   ├── server.js (Protected APIs only)
  │   ├── package.json
  │   └── Dockerfile
  ├── test-client.js (Updated)
  ├── docker-compose.yml
  └── package.json
```

**Three Separate Services:**
1. **OAuth Server** (Port 4000): Authorization & token management
2. **API Server** (Port 5000): Protected resources
3. **Test Client** (Port 3001): Demo application

## Key Architectural Changes

### 1. Token Validation

**Before:**
```javascript
// Direct access to in-memory tokens
const tokenData = accessTokens.get(token);
```

**After:**
```javascript
// API server calls OAuth server for validation
const response = await axios.post(`${OAUTH_SERVER}/oauth/introspect`, {
  token: token
});
```

### 2. Service Communication

**Before:**
- All code in same process
- Direct function calls
- Shared memory

**After:**
- HTTP-based communication
- RESTful API calls
- Independent processes

### 3. Deployment

**Before:**
```bash
node server.js  # Single command
```

**After:**
```bash
docker-compose up  # Orchestrated deployment
# OR
npm start  # In each service directory
```

## Breaking Changes

### Port Changes

| Service | Old Port | New Port |
|---------|----------|----------|
| OAuth Server | 3000 | 4000 |
| Protected APIs | 3000 | 5000 |
| Test Client | 3001 | 3001 (unchanged) |

### Endpoint URLs

**OAuth Endpoints:**
- Before: `http://localhost:3000/oauth/authorize`
- After: `http://localhost:4000/oauth/authorize`

**Protected APIs:**
- Before: `http://localhost:3000/api/protected`
- After: `http://localhost:5000/api/protected`

### Environment Variables

New environment variables needed:

**API Server:**
```bash
OAUTH_SERVER=http://localhost:4000  # URL of OAuth server
```

**Test Client:**
```bash
OAUTH_SERVER=http://localhost:4000
API_SERVER=http://localhost:5000
```

## Migration Steps

### For Development

1. **Stop old server:**
   ```bash
   # Kill any process on port 3000
   ```

2. **Start new services:**
   ```bash
   # Using Docker Compose
   docker-compose up --build
   
   # OR manually
   cd oauth-server && npm install && npm start &
   cd api-server && npm install && npm start &
   npm run client
   ```

### For Production

1. **Deploy OAuth Server:**
   ```bash
   cd oauth-server
   docker build -t oauth-server:latest .
   docker run -p 4000:4000 oauth-server:latest
   ```

2. **Deploy API Server:**
   ```bash
   cd api-server
   docker build -t api-server:latest .
   docker run -p 5000:5000 \
     -e OAUTH_SERVER=http://oauth-server:4000 \
     api-server:latest
   ```

3. **Update client configurations:**
   - Point OAuth calls to port 4000
   - Point API calls to port 5000

## Code Migration

### If You Extended the Old server.js

#### Adding OAuth-related features:
Edit `oauth-server/server.js`

Example - Add new grant type:
```javascript
// oauth-server/server.js
app.post('/oauth/token', (req, res) => {
  const { grant_type } = req.body;
  
  if (grant_type === 'client_credentials') {
    // Your new grant type logic
  }
  // ... existing code
});
```

#### Adding new protected APIs:
Edit `api-server/server.js`

Example - Add new endpoint:
```javascript
// api-server/server.js
app.get('/api/new-resource', validateToken, (req, res) => {
  res.json({
    message: 'New protected resource',
    client_id: req.tokenData.client_id
  });
});
```

## Benefits of New Architecture

### Separation of Concerns
- OAuth logic isolated from business logic
- Easier to maintain and test
- Clear boundaries between services

### Independent Scaling
```yaml
# Scale API server independently
docker-compose up --scale api-server=3
```

### Independent Deployment
- Update OAuth server without touching APIs
- Update APIs without OAuth downtime
- Deploy to different environments

### Technology Flexibility
- Could replace OAuth server with Keycloak/Auth0
- Could write API server in different language
- Each service can use different databases

### Better Security
- OAuth server can be in isolated network
- API servers in DMZ
- Reduced attack surface per service

## Rollback Plan

If you need to revert to monolithic architecture:

1. **Restore old server.js:**
   ```bash
   git checkout <old-commit> -- server.js
   ```

2. **Update test client:**
   ```javascript
   const OAUTH_SERVER = 'http://localhost:3000';
   ```

3. **Start old server:**
   ```bash
   node server.js
   ```

## Compatibility Notes

### Token Format
- Tokens are still opaque strings
- No changes to token structure
- Same validation logic

### OAuth2 Flow
- Authorization code flow unchanged
- Refresh token flow unchanged
- Same grant types supported

### Client Credentials
- `client_id` and `client_secret` unchanged
- Same test client works with both versions

## Testing

### Verify Migration

1. **Check health endpoints:**
   ```bash
   curl http://localhost:4000/health  # OAuth server
   curl http://localhost:5000/health  # API server
   ```

2. **Test OAuth flow:**
   ```bash
   # Visit test client
   open http://localhost:3001
   ```

3. **Test API access:**
   ```bash
   # Get token, then:
   curl -H "Authorization: Bearer $TOKEN" \
        http://localhost:5000/api/protected
   ```

## Common Issues

### Issue: Services can't communicate

**Symptom:**
```
Error: connect ECONNREFUSED 127.0.0.1:4000
```

**Solution:**
- Check all services are running: `docker-compose ps`
- Verify network configuration in docker-compose.yml
- Use service names (not localhost) in Docker network

### Issue: Token validation fails

**Symptom:**
```json
{"error": "invalid_token"}
```

**Solution:**
- Verify OAUTH_SERVER URL in API server config
- Check OAuth server is accessible
- Ensure tokens are being passed correctly

### Issue: CORS errors

**Symptom:**
```
Access-Control-Allow-Origin error
```

**Solution:**
- OAuth and API servers already have CORS enabled
- Check browser console for actual issue
- Verify redirect URIs match exactly

## Next Steps

1. Review [ARCHITECTURE.md](ARCHITECTURE.md) for detailed design
2. Read [SETUP.md](SETUP.md) for setup instructions
3. Explore adding new services to the architecture
4. Consider implementing JWT tokens
5. Add persistent storage (Redis/PostgreSQL)


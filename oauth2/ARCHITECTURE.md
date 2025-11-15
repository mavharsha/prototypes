# Architecture Overview

## System Architecture

This OAuth2 implementation follows a microservices architecture with three independent services that communicate over HTTP.

```
┌─────────────────────────────────────────────────────────────────┐
│                         Browser/Client                          │
└────────────┬───────────────────────────────────────┬────────────┘
             │                                       │
             │ (1) Authorization Request            │ (5) API Calls
             │                                       │ with Token
             ▼                                       ▼
┌─────────────────────────┐             ┌─────────────────────────┐
│                         │             │                         │
│    OAuth Server         │             │      API Server         │
│    (Port 4000)          │             │      (Port 5000)        │
│                         │             │                         │
│  ┌───────────────────┐  │             │  ┌───────────────────┐  │
│  │ Authorization     │  │             │  │ Protected APIs    │  │
│  │ Endpoint          │  │             │  │                   │  │
│  │ /oauth/authorize  │  │             │  │ /api/protected    │  │
│  └───────────────────┘  │             │  │ /api/user/profile │  │
│                         │             │  │ /api/user/update  │  │
│  ┌───────────────────┐  │             │  │ /api/data         │  │
│  │ Token Endpoint    │  │             │  └─────────┬─────────┘  │
│  │ /oauth/token      │  │             │            │            │
│  └───────────────────┘  │             │            │            │
│                         │             │            │            │
│  ┌───────────────────┐  │◄────────────┼────────────┘            │
│  │ Introspection     │  │ (4) Token   │                         │
│  │ /oauth/introspect │  │ Validation  │                         │
│  └───────────────────┘  │             │                         │
│                         │             │                         │
│  ┌───────────────────┐  │             │                         │
│  │ Token Storage     │  │             │                         │
│  │ (In-Memory)       │  │             │                         │
│  └───────────────────┘  │             │                         │
│                         │             │                         │
└─────────────────────────┘             └─────────────────────────┘
             ▲                                       
             │ (2) Authorization Code                
             │ (3) Token Exchange                    
             │                                       
┌────────────┴────────────┐
│                         │
│    Test Client          │
│    (Port 3001)          │
│                         │
│  ┌───────────────────┐  │
│  │ Web Interface     │  │
│  │ OAuth Flow Demo   │  │
│  └───────────────────┘  │
│                         │
└─────────────────────────┘
```

## Service Details

### 1. OAuth Server (oauth-server/)

**Responsibilities:**
- Handle authorization requests
- Issue authorization codes
- Exchange codes for access/refresh tokens
- Validate and introspect tokens
- Manage token lifecycle

**Key Endpoints:**
- `GET /oauth/authorize` - Initiate OAuth flow
- `POST /oauth/token` - Exchange code or refresh token
- `POST /oauth/introspect` - Validate tokens
- `GET /health` - Health check

**Storage:**
- Authorization codes (10 min TTL)
- Access tokens (1 hour TTL)
- Refresh tokens (no expiry)
- Client credentials

### 2. API Server (api-server/)

**Responsibilities:**
- Host protected API resources
- Validate incoming access tokens
- Enforce scope-based authorization
- Provide business logic endpoints

**Key Endpoints:**
- `GET /api/protected` - Basic protected resource
- `GET /api/user/profile` - User profile (read scope)
- `POST /api/user/update` - Update user (write scope)
- `GET /api/data` - Data access
- `GET /health` - Health check

**Token Validation:**
- Extracts Bearer token from Authorization header
- Calls OAuth Server's introspection endpoint
- Validates token is active and not expired
- Checks scopes for protected operations

### 3. Test Client (test-client.js)

**Responsibilities:**
- Provide web UI for testing
- Implement OAuth2 authorization code flow
- Demonstrate token management
- Proxy API requests

**Features:**
- Interactive authorization flow
- Token display and management
- Protected API call examples
- Token refresh demonstration

## OAuth2 Authorization Code Flow

```
┌────────┐                                           ┌─────────────┐
│        │──(1) Authorization Request───────────────>│             │
│        │                                           │             │
│        │<─(2) Authorization Code──────────────────│   OAuth     │
│ Client │                                           │   Server    │
│        │──(3) Token Request (with code)───────────>│             │
│        │                                           │             │
│        │<─(4) Access Token + Refresh Token─────────│             │
└────────┘                                           └─────────────┘
     │                                                      ▲
     │                                                      │
     │ (5) API Request (with token)                       │
     │                                                      │
     ▼                                                      │
┌─────────────┐                                            │
│             │──(6) Validate Token (introspect)──────────┘
│ API Server  │
│             │<─(7) Token Info (active, scopes)──────────┐
└─────────────┘
```

### Flow Steps:

1. **Authorization Request**: Client redirects user to OAuth server with client_id, redirect_uri, scope
2. **Authorization Code**: OAuth server redirects back with temporary code (10 min validity)
3. **Token Request**: Client exchanges code for tokens using client_secret (server-to-server)
4. **Token Response**: OAuth server returns access_token and refresh_token
5. **API Request**: Client calls API with Bearer token in Authorization header
6. **Token Introspection**: API server validates token with OAuth server
7. **Token Info**: OAuth server confirms token is active and returns metadata
8. **API Response**: API server returns protected resource data

## Communication Patterns

### Service-to-Service Communication

```
API Server → OAuth Server
- Protocol: HTTP POST
- Endpoint: /oauth/introspect
- Purpose: Real-time token validation
- Frequency: Every protected API request
```

### Client-to-Service Communication

```
Client → OAuth Server
- Authorization flow initiation
- Token exchange
- Token refresh

Client → API Server
- Protected resource access
- Business operations
```

## Security Considerations

### Current Implementation (Demo)
- In-memory token storage
- Auto-approved authorization
- No user authentication
- HTTP communication
- Shared secrets in code

### Production Requirements
1. **Use HTTPS** for all communication
2. **Database storage** for tokens and clients
3. **User authentication** system
4. **Secure secret management** (env vars, vault)
5. **Rate limiting** on all endpoints
6. **PKCE** for public clients
7. **Token encryption** at rest
8. **Audit logging** for all operations
9. **CSRF protection**
10. **Input validation and sanitization**

## Deployment Architecture

### Docker Compose (Development)
```yaml
networks:
  oauth-network:
    - oauth-server (4000)
    - api-server (5000)
    - test-client (3001)
```

### Production Deployment Options

1. **Kubernetes**
   ```
   - OAuth Server Deployment (3 replicas)
   - API Server Deployment (5 replicas)
   - Client Deployment (2 replicas)
   - Redis for token storage
   - PostgreSQL for persistent data
   ```

2. **Cloud Services**
   ```
   - OAuth: AWS Cognito / Auth0 / Keycloak
   - API: Lambda / Cloud Run / App Service
   - Storage: RDS / DynamoDB
   - Load Balancer: ALB / Cloud Load Balancer
   ```

## Scalability

### Horizontal Scaling

**OAuth Server:**
- Stateless after moving to shared storage
- Can scale behind load balancer
- Requires shared Redis/database for tokens

**API Server:**
- Fully stateless
- Easy horizontal scaling
- Only depends on OAuth server availability

**Considerations:**
- Token introspection can become bottleneck
- Consider token caching on API server
- Use JWT tokens to reduce introspection calls
- Implement circuit breakers for resilience

## Token Strategy

### Current: Opaque Tokens
- **Pros**: Easy revocation, secure
- **Cons**: Requires introspection call per request

### Alternative: JWT Tokens
- **Pros**: Self-contained, no introspection needed
- **Cons**: Harder to revoke, larger payload

### Hybrid Approach
- Use JWT for access tokens (short-lived)
- Keep opaque refresh tokens
- Cache introspection results
- Implement token blacklist for revocation

## Extension Points

### Add New Services
1. Create new service directory
2. Add to docker-compose.yml
3. Configure to use OAuth server for auth
4. Define required scopes

### Add New OAuth Flows
- Implicit Flow (SPA apps)
- Client Credentials (service-to-service)
- Resource Owner Password (legacy apps)
- Device Authorization (IoT devices)

### Add Features
- User registration/login
- Consent screen
- Scope management UI
- Admin dashboard
- Analytics and monitoring


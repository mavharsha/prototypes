# Basic OAuth2 Server

A simple OAuth2 authorization server implementation in JavaScript using Express.js, without external OAuth2 libraries.

## Features

- **Authorization Code Flow**: Complete implementation of the OAuth2 authorization code grant
- **Token Management**: Access tokens and refresh tokens with expiration
- **Client Validation**: Basic client authentication and validation
- **Protected Resources**: Example protected endpoint
- **Token Introspection**: Endpoint to validate token status
- **Test Client**: Interactive web client to demonstrate the OAuth2 flow

## Project Structure

```
oauth2/
├── server.js          # OAuth2 authorization server
├── test-client.js     # Test client application
├── package.json       # Dependencies and scripts
└── README.md          # This file
```

## Installation

1. Install dependencies:
```bash
npm install
```

## Running the Server

### Start the OAuth2 Server
```bash
npm start
# or
node server.js
```

The server will run on `http://localhost:3000`

### Start the Test Client
```bash
npm run client
# or
node test-client.js
```

The test client will run on `http://localhost:3001`

## OAuth2 Endpoints

### Authorization Server (Port 3000)

- `GET /oauth/authorize` - Authorization endpoint
- `POST /oauth/token` - Token endpoint
- `GET /api/protected` - Protected resource endpoint
- `POST /oauth/introspect` - Token introspection endpoint
- `GET /health` - Health check endpoint

### Test Client (Port 3001)

- `GET /` - Interactive OAuth2 flow demonstration
- `GET /callback` - OAuth2 callback handler
- `GET /api/protected` - Proxy to protected resource
- `POST /refresh-token` - Token refresh endpoint

## OAuth2 Flow Demonstration

1. **Start both servers**:
   ```bash
   # Terminal 1
   npm start
   
   # Terminal 2
   npm run client
   ```

2. **Open the test client**: Navigate to `http://localhost:3001`

3. **Follow the OAuth2 flow**:
   - Click "Authorize with OAuth2 Server"
   - You'll be redirected to the authorization server
   - The server will automatically approve the request (for demo purposes)
   - You'll be redirected back with an authorization code
   - The client will exchange the code for access and refresh tokens
   - Use the "Access Protected Resource" button to test the protected endpoint
   - Use the "Refresh Access Token" button to test token refresh

## Client Configuration

The server comes pre-configured with a test client:

- **Client ID**: `test-client`
- **Client Secret**: `test-secret`
- **Redirect URI**: `http://localhost:3001/callback`
- **Scopes**: `read`, `write`

## API Examples

### Authorization Request
```
GET /oauth/authorize?client_id=test-client&redirect_uri=http://localhost:3001/callback&response_type=code&scope=read+write&state=test123
```

### Token Exchange
```bash
curl -X POST http://localhost:3000/oauth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code&code=AUTH_CODE&redirect_uri=http://localhost:3001/callback&client_id=test-client&client_secret=test-secret"
```

### Access Protected Resource
```bash
curl -H "Authorization: Bearer ACCESS_TOKEN" http://localhost:3000/api/protected
```

### Token Introspection
```bash
curl -X POST http://localhost:3000/oauth/introspect \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "token=ACCESS_TOKEN"
```

## Security Notes

This is a **demo implementation** for educational purposes. For production use, consider:

- Use a proper database instead of in-memory storage
- Implement proper user authentication and consent screens
- Add CSRF protection
- Use HTTPS in production
- Implement proper token storage and rotation
- Add rate limiting and security headers
- Validate all inputs thoroughly
- Use secure random token generation

## OAuth2 Grant Types Supported

- **Authorization Code Grant**: Primary flow for web applications
- **Refresh Token Grant**: For obtaining new access tokens

## Token Types

- **Access Tokens**: Bearer tokens for API access (1 hour expiration)
- **Refresh Tokens**: Long-lived tokens for obtaining new access tokens
- **Authorization Codes**: Short-lived codes for token exchange (10 minutes)

## Error Handling

The server implements standard OAuth2 error responses:

- `invalid_request`: Missing or malformed parameters
- `invalid_client`: Invalid client credentials
- `invalid_grant`: Invalid authorization code or refresh token
- `unsupported_grant_type`: Unsupported grant type
- `invalid_token`: Invalid or expired access token

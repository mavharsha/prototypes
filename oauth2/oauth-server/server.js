const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const crypto = require('crypto');

const app = express();
const PORT = process.env.PORT || 4000;

// Middleware
app.use(cors());
app.use(bodyParser.json());
app.use(bodyParser.urlencoded({ extended: true }));

// In-memory storage (in production, use a database)
const clients = new Map();
const authorizationCodes = new Map();
const accessTokens = new Map();
const refreshTokens = new Map();

// Sample client data
clients.set('test-client', {
  clientId: 'test-client',
  clientSecret: 'test-secret',
  redirectUri: 'http://localhost:3001/callback',
  scopes: ['read', 'write']
});

// Helper functions
function generateToken() {
  return crypto.randomBytes(32).toString('hex');
}

function generateAuthCode() {
  return crypto.randomBytes(16).toString('hex');
}

function validateClient(clientId, clientSecret) {
  const client = clients.get(clientId);
  return client && client.clientSecret === clientSecret;
}

function validateRedirectUri(clientId, redirectUri) {
  const client = clients.get(clientId);
  return client && client.redirectUri === redirectUri;
}

// OAuth2 Authorization Endpoint
app.get('/oauth/authorize', (req, res) => {
  const { client_id, redirect_uri, response_type, scope, state } = req.query;
  
  // Validate required parameters
  if (!client_id || !redirect_uri || !response_type) {
    return res.status(400).json({ error: 'invalid_request', error_description: 'Missing required parameters' });
  }
  
  // Validate client
  if (!clients.has(client_id)) {
    return res.status(400).json({ error: 'invalid_client' });
  }
  
  // Validate redirect URI
  if (!validateRedirectUri(client_id, redirect_uri)) {
    return res.status(400).json({ error: 'invalid_request', error_description: 'Invalid redirect URI' });
  }
  
  // For demo purposes, we'll simulate user consent
  // In a real app, you'd show a consent page to the user
  if (response_type === 'code') {
    const authCode = generateAuthCode();
    const expiresAt = Date.now() + (10 * 60 * 1000); // 10 minutes
    
    authorizationCodes.set(authCode, {
      clientId: client_id,
      redirectUri: redirect_uri,
      scope: scope || 'read',
      expiresAt: expiresAt
    });
    
    // Redirect back to client with authorization code
    const redirectUrl = new URL(redirect_uri);
    redirectUrl.searchParams.set('code', authCode);
    if (state) redirectUrl.searchParams.set('state', state);
    
    return res.redirect(redirectUrl.toString());
  }
  
  res.status(400).json({ error: 'unsupported_response_type' });
});

// OAuth2 Token Endpoint
app.post('/oauth/token', (req, res) => {
  const { grant_type, code, redirect_uri, client_id, client_secret } = req.body;
  
  // Validate required parameters
  if (!grant_type || !client_id) {
    return res.status(400).json({ error: 'invalid_request' });
  }
  
  // Validate client
  if (!validateClient(client_id, client_secret)) {
    return res.status(401).json({ error: 'invalid_client' });
  }
  
  if (grant_type === 'authorization_code') {
    // Validate authorization code
    if (!code || !authorizationCodes.has(code)) {
      return res.status(400).json({ error: 'invalid_grant' });
    }
    
    const authCodeData = authorizationCodes.get(code);
    
    // Check if code has expired
    if (Date.now() > authCodeData.expiresAt) {
      authorizationCodes.delete(code);
      return res.status(400).json({ error: 'invalid_grant', error_description: 'Authorization code expired' });
    }
    
    // Validate redirect URI
    if (redirect_uri !== authCodeData.redirectUri) {
      return res.status(400).json({ error: 'invalid_grant', error_description: 'Redirect URI mismatch' });
    }
    
    // Generate tokens
    const accessToken = generateToken();
    const refreshToken = generateToken();
    const expiresIn = 3600; // 1 hour
    
    // Store tokens
    accessTokens.set(accessToken, {
      clientId: client_id,
      scope: authCodeData.scope,
      expiresAt: Date.now() + (expiresIn * 1000)
    });
    
    refreshTokens.set(refreshToken, {
      clientId: client_id,
      scope: authCodeData.scope
    });
    
    // Clean up authorization code
    authorizationCodes.delete(code);
    
    // Return tokens
    res.json({
      access_token: accessToken,
      token_type: 'Bearer',
      expires_in: expiresIn,
      refresh_token: refreshToken,
      scope: authCodeData.scope
    });
    
  } else if (grant_type === 'refresh_token') {
    const { refresh_token } = req.body;
    
    if (!refresh_token || !refreshTokens.has(refresh_token)) {
      return res.status(400).json({ error: 'invalid_grant' });
    }
    
    const refreshTokenData = refreshTokens.get(refresh_token);
    
    // Generate new access token
    const newAccessToken = generateToken();
    const expiresIn = 3600;
    
    accessTokens.set(newAccessToken, {
      clientId: client_id,
      scope: refreshTokenData.scope,
      expiresAt: Date.now() + (expiresIn * 1000)
    });
    
    res.json({
      access_token: newAccessToken,
      token_type: 'Bearer',
      expires_in: expiresIn,
      scope: refreshTokenData.scope
    });
    
  } else {
    res.status(400).json({ error: 'unsupported_grant_type' });
  }
});

// Token introspection endpoint
app.post('/oauth/introspect', (req, res) => {
  const { token, token_type_hint } = req.body;
  
  if (!token) {
    return res.status(400).json({ error: 'invalid_request' });
  }
  
  const tokenData = accessTokens.get(token);
  
  if (!tokenData || Date.now() > tokenData.expiresAt) {
    return res.json({ active: false });
  }
  
  res.json({
    active: true,
    client_id: tokenData.clientId,
    scope: tokenData.scope,
    exp: Math.floor(tokenData.expiresAt / 1000)
  });
});

// Health check endpoint
app.get('/health', (req, res) => {
  res.json({ 
    status: 'OK', 
    service: 'oauth-server',
    timestamp: new Date().toISOString(),
    active_tokens: accessTokens.size,
    active_codes: authorizationCodes.size
  });
});

// Start server
app.listen(PORT, () => {
  console.log(`OAuth2 Server running on http://localhost:${PORT}`);
  console.log('Available endpoints:');
  console.log('  GET  /oauth/authorize - Authorization endpoint');
  console.log('  POST /oauth/token - Token endpoint');
  console.log('  POST /oauth/introspect - Token introspection');
  console.log('  GET  /health - Health check');
  console.log('\nTest client credentials:');
  console.log('  client_id: test-client');
  console.log('  client_secret: test-secret');
  console.log('  redirect_uri: http://localhost:3001/callback');
});


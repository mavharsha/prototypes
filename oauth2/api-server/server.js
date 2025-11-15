const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const axios = require('axios');

const app = express();
const PORT = process.env.PORT || 5000;
const OAUTH_SERVER = process.env.OAUTH_SERVER || 'http://localhost:4000';

// Middleware
app.use(cors());
app.use(bodyParser.json());

// Middleware to validate access token
async function validateToken(req, res, next) {
  const authHeader = req.headers.authorization;
  
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ 
      error: 'invalid_token', 
      error_description: 'Missing or invalid authorization header' 
    });
  }
  
  const token = authHeader.substring(7);
  
  try {
    // Call OAuth server to introspect token
    const introspectionResponse = await axios.post(`${OAUTH_SERVER}/oauth/introspect`, {
      token: token,
      token_type_hint: 'access_token'
    });
    
    const tokenData = introspectionResponse.data;
    
    if (!tokenData.active) {
      return res.status(401).json({ 
        error: 'invalid_token', 
        error_description: 'Invalid or expired access token' 
      });
    }
    
    // Attach token data to request for use in handlers
    req.tokenData = tokenData;
    next();
    
  } catch (error) {
    console.error('Token validation error:', error.message);
    return res.status(500).json({ 
      error: 'server_error', 
      error_description: 'Failed to validate token' 
    });
  }
}

// Protected Resource Endpoints
app.get('/api/protected', validateToken, (req, res) => {
  res.json({
    message: 'This is a protected resource!',
    client_id: req.tokenData.client_id,
    scope: req.tokenData.scope,
    timestamp: new Date().toISOString()
  });
});

app.get('/api/user/profile', validateToken, (req, res) => {
  // Check if token has required scope
  if (!req.tokenData.scope || !req.tokenData.scope.includes('read')) {
    return res.status(403).json({ 
      error: 'insufficient_scope', 
      error_description: 'Token does not have required scope' 
    });
  }
  
  res.json({
    user: {
      id: '123',
      username: 'demo_user',
      email: 'user@example.com',
      name: 'Demo User'
    },
    client_id: req.tokenData.client_id,
    timestamp: new Date().toISOString()
  });
});

app.post('/api/user/update', validateToken, (req, res) => {
  // Check if token has required scope
  if (!req.tokenData.scope || !req.tokenData.scope.includes('write')) {
    return res.status(403).json({ 
      error: 'insufficient_scope', 
      error_description: 'Token does not have write scope' 
    });
  }
  
  const { name, email } = req.body;
  
  res.json({
    message: 'User updated successfully',
    updated: {
      name,
      email
    },
    timestamp: new Date().toISOString()
  });
});

app.get('/api/data', validateToken, (req, res) => {
  res.json({
    data: [
      { id: 1, value: 'Item 1' },
      { id: 2, value: 'Item 2' },
      { id: 3, value: 'Item 3' }
    ],
    client_id: req.tokenData.client_id,
    timestamp: new Date().toISOString()
  });
});

// Health check endpoint (not protected)
app.get('/health', (req, res) => {
  res.json({ 
    status: 'OK',
    service: 'api-server',
    timestamp: new Date().toISOString(),
    oauth_server: OAUTH_SERVER
  });
});

// Start server
app.listen(PORT, () => {
  console.log(`API Server running on http://localhost:${PORT}`);
  console.log(`OAuth Server: ${OAUTH_SERVER}`);
  console.log('Available endpoints:');
  console.log('  GET  /api/protected - Protected resource (requires auth)');
  console.log('  GET  /api/user/profile - User profile (requires read scope)');
  console.log('  POST /api/user/update - Update user (requires write scope)');
  console.log('  GET  /api/data - Get data (requires auth)');
  console.log('  GET  /health - Health check');
});


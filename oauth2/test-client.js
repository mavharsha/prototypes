const express = require('express');
const axios = require('axios');

const app = express();
const PORT = 3001;

// OAuth2 configuration
const OAUTH_SERVER = 'http://localhost:3000';
const CLIENT_ID = 'test-client';
const CLIENT_SECRET = 'test-secret';
const REDIRECT_URI = 'http://localhost:3001/callback';

// Store for demo purposes
let accessToken = null;
let refreshToken = null;

// Serve static HTML for demo
app.get('/', (req, res) => {
  res.send(`
    <!DOCTYPE html>
    <html>
    <head>
        <title>OAuth2 Test Client</title>
        <style>
            body { font-family: Arial, sans-serif; margin: 40px; }
            .container { max-width: 600px; }
            button { padding: 10px 20px; margin: 10px 5px; background: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer; }
            button:hover { background: #0056b3; }
            button:disabled { background: #6c757d; cursor: not-allowed; }
            .token { background: #f8f9fa; padding: 10px; border-radius: 4px; margin: 10px 0; word-break: break-all; }
            .success { color: #28a745; }
            .error { color: #dc3545; }
        </style>
    </head>
    <body>
        <div class="container">
            <h1>OAuth2 Test Client</h1>
            <p>This is a test client to demonstrate OAuth2 authorization code flow.</p>
            
            <h2>Step 1: Start Authorization</h2>
            <button onclick="startAuth()">Authorize with OAuth2 Server</button>
            
            <h2>Step 2: Access Protected Resource</h2>
            <button id="protected-btn" onclick="accessProtected()" disabled>Access Protected Resource</button>
            
            <h2>Step 3: Refresh Token</h2>
            <button id="refresh-btn" onclick="refreshAccessToken()" disabled>Refresh Access Token</button>
            
            <h2>Current Tokens</h2>
            <div id="tokens">
                <p><strong>Access Token:</strong> <span id="access-token">None</span></p>
                <p><strong>Refresh Token:</strong> <span id="refresh-token">None</span></p>
            </div>
            
            <h2>API Response</h2>
            <div id="response"></div>
        </div>
        
        <script>
            let accessToken = null;
            let refreshToken = null;
            
            function startAuth() {
                const authUrl = '${OAUTH_SERVER}/oauth/authorize?' + 
                    'client_id=${CLIENT_ID}&' +
                    'redirect_uri=${encodeURIComponent(REDIRECT_URI)}&' +
                    'response_type=code&' +
                    'scope=read+write&' +
                    'state=test123';
                window.location.href = authUrl;
            }
            
            function accessProtected() {
                if (!accessToken) {
                    showResponse('No access token available', 'error');
                    return;
                }
                
                fetch('/api/protected')
                    .then(response => response.json())
                    .then(data => showResponse(JSON.stringify(data, null, 2), 'success'))
                    .catch(error => showResponse('Error: ' + error.message, 'error'));
            }
            
            function refreshAccessToken() {
                if (!refreshToken) {
                    showResponse('No refresh token available', 'error');
                    return;
                }
                
                fetch('/refresh-token', { method: 'POST' })
                    .then(response => response.json())
                    .then(data => {
                        if (data.access_token) {
                            accessToken = data.access_token;
                            document.getElementById('access-token').textContent = accessToken;
                            document.getElementById('protected-btn').disabled = false;
                            showResponse('Token refreshed successfully: ' + JSON.stringify(data, null, 2), 'success');
                        } else {
                            showResponse('Error refreshing token: ' + JSON.stringify(data, null, 2), 'error');
                        }
                    })
                    .catch(error => showResponse('Error: ' + error.message, 'error'));
            }
            
            function showResponse(message, type) {
                const responseDiv = document.getElementById('response');
                responseDiv.innerHTML = '<pre class="' + type + '">' + message + '</pre>';
            }
            
            // Check for tokens in URL parameters (from callback)
            function checkForTokens() {
                const urlParams = new URLSearchParams(window.location.search);
                const accessTokenParam = urlParams.get('access_token');
                const refreshTokenParam = urlParams.get('refresh_token');
                const expiresIn = urlParams.get('expires_in');
                const scope = urlParams.get('scope');
                
                if (accessTokenParam) {
                    accessToken = accessTokenParam;
                    refreshToken = refreshTokenParam;
                    
                    // Update UI
                    document.getElementById('access-token').textContent = accessToken;
                    document.getElementById('refresh-token').textContent = refreshToken;
                    document.getElementById('protected-btn').disabled = false;
                    document.getElementById('refresh-btn').disabled = false;
                    
                    showResponse('Tokens received successfully!\\n' +
                        'Access Token: ' + accessToken + '\\n' +
                        'Refresh Token: ' + refreshToken + '\\n' +
                        'Expires In: ' + expiresIn + ' seconds\\n' +
                        'Scope: ' + scope, 'success');
                    
                    // Clean up URL
                    window.history.replaceState({}, document.title, window.location.pathname);
                }
            }
            
            // Initialize on page load
            checkForTokens();
        </script>
    </body>
    </html>
  `);
});

// Handle OAuth2 callback
app.get('/callback', async (req, res) => {
  const { code, state, error } = req.query;
  
  if (error) {
    return res.send(`
      <h1>Authorization Error</h1>
      <p>Error: ${error}</p>
      <a href="/">Back to Home</a>
    `);
  }
  
  if (!code) {
    return res.send(`
      <h1>Authorization Error</h1>
      <p>No authorization code received</p>
      <a href="/">Back to Home</a>
    `);
  }
  
  try {
    // Exchange authorization code for access token
    const tokenResponse = await axios.post(`${OAUTH_SERVER}/oauth/token`, {
      grant_type: 'authorization_code',
      code: code,
      redirect_uri: REDIRECT_URI,
      client_id: CLIENT_ID,
      client_secret: CLIENT_SECRET
    });
    
    const { access_token, refresh_token, expires_in, scope } = tokenResponse.data;
    
    // Store tokens (in a real app, use secure storage)
    accessToken = access_token;
    refreshToken = refresh_token;
    
    // Redirect to main page with tokens in URL for client-side JavaScript
    const redirectUrl = new URL('/', req.protocol + '://' + req.get('host'));
    redirectUrl.searchParams.set('access_token', access_token);
    redirectUrl.searchParams.set('refresh_token', refresh_token);
    redirectUrl.searchParams.set('expires_in', expires_in);
    redirectUrl.searchParams.set('scope', scope);
    
    res.redirect(redirectUrl.toString());
    
  } catch (error) {
    console.error('Token exchange error:', error.response?.data || error.message);
    res.send(`
      <h1>Token Exchange Error</h1>
      <p>Error: ${error.response?.data?.error_description || error.message}</p>
      <a href="/">Back to Home</a>
    `);
  }
});

// Proxy protected resource requests
app.get('/api/protected', async (req, res) => {
  if (!accessToken) {
    return res.status(401).json({ error: 'No access token available' });
  }
  
  try {
    const response = await axios.get(`${OAUTH_SERVER}/api/protected`, {
      headers: {
        'Authorization': `Bearer ${accessToken}`
      }
    });
    
    res.json(response.data);
  } catch (error) {
    res.status(error.response?.status || 500).json(error.response?.data || { error: error.message });
  }
});

// Refresh token endpoint
app.post('/refresh-token', async (req, res) => {
  if (!refreshToken) {
    return res.status(400).json({ error: 'No refresh token available' });
  }
  
  try {
    const response = await axios.post(`${OAUTH_SERVER}/oauth/token`, {
      grant_type: 'refresh_token',
      refresh_token: refreshToken,
      client_id: CLIENT_ID,
      client_secret: CLIENT_SECRET
    });
    
    const { access_token, expires_in, scope } = response.data;
    accessToken = access_token;
    
    res.json({
      access_token,
      expires_in,
      scope
    });
    
  } catch (error) {
    res.status(error.response?.status || 500).json(error.response?.data || { error: error.message });
  }
});

app.listen(PORT, () => {
  console.log(`Test Client running on http://localhost:${PORT}`);
  console.log('Make sure the OAuth2 server is running on http://localhost:3000');
});

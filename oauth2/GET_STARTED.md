# 🚀 Get Started in 2 Minutes

## What is This?

A **complete OAuth2 implementation** with separated microservices architecture:
- **OAuth Server** - Issues and validates tokens
- **API Server** - Hosts protected resources  
- **Test Client** - Interactive demo web app

## ⚡ Quick Start

### Option 1: Docker (Easiest)

```bash
docker-compose up --build
```

Then open: **http://localhost:3001**

### Option 2: Windows

```powershell
.\start-services.ps1
```

### Option 3: Linux/Mac

```bash
chmod +x start-services.sh
./start-services.sh
```

## 🎮 Try It Out

1. **Open browser:** http://localhost:3001
2. **Click:** "Authorize with OAuth2 Server"
3. **Get tokens** automatically (via redirect)
4. **Click:** "Access Protected Resource"
5. **See:** Your protected data returned!

## 🌐 Access Points

| What | URL | Description |
|------|-----|-------------|
| **Test Client** | http://localhost:3001 | Try OAuth flow here! |
| **OAuth Server** | http://localhost:4000 | Authorization & tokens |
| **API Server** | http://localhost:5000 | Protected APIs |

## 📖 What to Read Next?

### Just Starting?
👉 You're in the right place! Just run `docker-compose up` and visit http://localhost:3001

### Want to Understand?
👉 Read [ARCHITECTURE.md](ARCHITECTURE.md) for system design

### Setting Up Manually?
👉 Read [SETUP.md](SETUP.md) for detailed instructions

### Need Quick Commands?
👉 Check [QUICK_REFERENCE.md](QUICK_REFERENCE.md)

### Migrating from Old Version?
👉 See [MIGRATION.md](MIGRATION.md)

## 🎯 Common Tasks

### Start Services
```bash
docker-compose up
```

### Stop Services
```bash
docker-compose down
```

### View Logs
```bash
docker-compose logs -f
```

### Test with curl
```bash
# 1. Get token (use browser)
http://localhost:4000/oauth/authorize?client_id=test-client&redirect_uri=http://localhost:3001/callback&response_type=code&scope=read+write

# 2. Exchange code for token
curl -X POST http://localhost:4000/oauth/token \
  -H "Content-Type: application/json" \
  -d '{"grant_type":"authorization_code","code":"YOUR_CODE","redirect_uri":"http://localhost:3001/callback","client_id":"test-client","client_secret":"test-secret"}'

# 3. Call protected API
curl -H "Authorization: Bearer YOUR_TOKEN" \
  http://localhost:5000/api/protected
```

## 🏗️ Project Structure

```
oauth2/
├── 🔐 oauth-server/         # OAuth authorization server
│   ├── server.js
│   ├── Dockerfile
│   └── package.json
│
├── 🛡️ api-server/           # Protected API server
│   ├── server.js
│   ├── Dockerfile
│   └── package.json
│
├── 🎨 test-client.js        # Demo web application
│
├── 🐳 docker-compose.yml    # One-command startup
│
└── 📚 Documentation/
    ├── README.md            # Overview
    ├── GET_STARTED.md       # You are here!
    ├── SETUP.md             # Detailed setup
    ├── ARCHITECTURE.md      # System design
    ├── QUICK_REFERENCE.md   # Command reference
    └── MIGRATION.md         # Migration guide
```

## 💡 What Can I Do?

### Learn OAuth2
- See authorization code flow in action
- Understand token exchange
- Try token refresh mechanism
- Explore scope-based authorization

### Build On It
- Add new API endpoints
- Implement new OAuth flows
- Add user authentication
- Connect to real database
- Deploy to cloud

### Experiment
- Modify scopes
- Change token expiration
- Add new clients
- Try different grant types

## 🔑 Test Credentials

```
Client ID:      test-client
Client Secret:  test-secret
Redirect URI:   http://localhost:3001/callback
Scopes:         read, write
```

## 🆘 Something Wrong?

### Services won't start?
```bash
# Check if ports are available
netstat -an | findstr "4000 5000 3001"

# Kill existing processes
docker-compose down
```

### Can't access services?
```bash
# Check if running
docker-compose ps

# Check logs
docker-compose logs
```

### Still stuck?
- Check [SETUP.md](SETUP.md) for detailed instructions
- See [QUICK_REFERENCE.md](QUICK_REFERENCE.md) troubleshooting section

## 🎓 Learning Path

```
1. Start services (docker-compose up)
2. Try test client (http://localhost:3001)
3. Read ARCHITECTURE.md (understand the flow)
4. Check QUICK_REFERENCE.md (explore APIs)
5. Modify and experiment!
```

## ✨ Features

✅ Complete OAuth2 Authorization Code Flow  
✅ Token Refresh  
✅ Scope-based Authorization  
✅ Token Introspection  
✅ Microservices Architecture  
✅ Docker Compose Support  
✅ Interactive Test Client  
✅ Multiple Protected Endpoints  
✅ Health Checks  
✅ CORS Enabled  

## 🎉 You're Ready!

Run this command and start exploring:

```bash
docker-compose up --build
```

Then visit: **http://localhost:3001**

Have fun! 🚀

---

📚 **More Info:** [README.md](README.md) | [ARCHITECTURE.md](ARCHITECTURE.md) | [SETUP.md](SETUP.md)


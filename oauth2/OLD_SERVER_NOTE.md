# Note About server.js in Root Directory

## ⚠️ This file is from the old monolithic architecture

The `server.js` file in the root directory (`oauth2/server.js`) is the **original monolithic implementation** where OAuth server and protected APIs were combined in a single service.

## Current Architecture

The application has been **refactored into microservices**:

```
oauth2/
  ├── oauth-server/          ← OAuth endpoints (NEW)
  │   └── server.js
  ├── api-server/            ← Protected APIs (NEW)
  │   └── server.js
  └── server.js              ← Old monolithic version (LEGACY)
```

## What to Use?

### ✅ Use the New Architecture (Recommended)

Start services with:
```bash
docker-compose up --build
```

Or manually:
```bash
cd oauth-server && npm start
cd api-server && npm start
npm run client
```

### 🔴 Old Monolithic Version (Not Recommended)

If you really need the old version:
```bash
node server.js  # Runs on port 3000
```

**Note:** The old server.js is kept for reference but not maintained.

## Key Differences

| Feature | Old (server.js) | New (Microservices) |
|---------|----------------|---------------------|
| Services | 1 combined | 3 separate |
| Ports | 3000 | 4000, 5000, 3001 |
| Scalability | Limited | Independent scaling |
| Deployment | Single process | Docker Compose |
| Token Validation | In-memory | HTTP introspection |

## Migration

If you've been using the old server.js, see [MIGRATION.md](MIGRATION.md) for migration guide.

## Why Keep It?

The old server.js is kept to:
1. Show the evolution of the architecture
2. Serve as a simpler reference implementation
3. Allow comparison between architectures
4. Support any existing integrations during transition

## Recommendation

**For new projects or features, use the microservices architecture** in `oauth-server/` and `api-server/` directories.


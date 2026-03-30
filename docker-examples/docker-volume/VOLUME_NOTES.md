# Docker Volumes: Named Volumes vs Bind Mounts

Docker containers are ephemeral — when a container is removed, its filesystem is gone.
Volumes solve this by providing persistent storage. There are two main types:

---

## 1. Bind Mounts

A bind mount maps a **specific file or directory on the host** into the container.
You control exactly where the data lives on your machine.

### Syntax

```yaml
# docker-compose.yml
services:
  app:
    volumes:
      - ./config/nginx.conf:/etc/nginx/conf.d/default.conf    # file mount
      - ./src:/app/src                                         # directory mount
      - ./data:/data:ro                                        # read-only mount
```

```bash
# docker CLI
docker run -v /absolute/path/on/host:/path/in/container image
docker run -v $(pwd)/src:/app/src image
```

### How It Works

```
Host Machine                    Container
-----------                     ---------
/home/user/project/
  |-- src/                 -->  /app/src/          (same files, live sync)
  |-- nginx.conf           -->  /etc/nginx/...     (same file, live sync)
```

- The container sees the **exact same bytes** as the host.
- Changes on either side are reflected immediately (unless `:ro`).
- If the host path doesn't exist, Docker creates it as an **empty directory** (not a file).

### Practical Examples

#### Example 1: Hot-reloading source code during development

```yaml
version: '3.8'
services:
  web:
    image: node:18
    working_dir: /app
    command: npm run dev
    volumes:
      - ./src:/app/src          # edit code on host, container picks it up instantly
      - ./package.json:/app/package.json
    ports:
      - "3000:3000"
```

- You edit `src/index.js` in your IDE -> the container sees the change immediately.
- No image rebuild needed for code changes.

#### Example 2: Custom Nginx config (like the random-number-api example)

```yaml
services:
  nginx:
    image: nginx:alpine
    volumes:
      - ./nginx.conf:/etc/nginx/conf.d/default.conf:ro
    ports:
      - "80:80"
```

- `:ro` = read-only. The container can read the file but cannot modify it.
- Swaps out Nginx's default config with your custom one.

#### Example 3: Sharing a log directory with the host

```yaml
services:
  app:
    build: .
    volumes:
      - ./logs:/var/log/app      # logs written inside container appear on host
```

```bash
# on your host machine
tail -f ./logs/app.log           # watch logs without docker exec
```

### When to Use Bind Mounts

| Use Case | Why |
|----------|-----|
| Local development (hot reload) | Edit on host, reflect in container instantly |
| Config file injection | Swap in custom configs without rebuilding images |
| Accessing host files from container | Container needs to read/process host files |
| Debugging | Inspect container output directly on host filesystem |

### Gotchas

- **Path must be absolute** in Docker CLI (`-v /full/path:/container/path`). In compose, relative paths like `./` are resolved from the compose file location.
- **Host path wins**: If the host directory is empty, it will **override** whatever was in the container at that path. This can accidentally hide files that existed in the image.
- **OS differences**: File permissions and line endings can cause issues across Linux/Mac/Windows.
- **No Docker management**: Docker doesn't track bind mounts — `docker volume ls` won't show them.

---

## 2. Named Volumes

A named volume is storage **managed entirely by Docker**. You give it a name, and Docker handles where it's stored on disk.

### Syntax

```yaml
# docker-compose.yml
services:
  db:
    image: postgres:15
    volumes:
      - pgdata:/var/lib/postgresql/data    # use the named volume

volumes:
  pgdata:                                   # declare it
    driver: local
```

```bash
# docker CLI
docker volume create pgdata
docker run -v pgdata:/var/lib/postgresql/data postgres:15
```

### How It Works

```
Host Machine (managed by Docker)          Container
------------------------------------      ---------
/var/lib/docker/volumes/pgdata/_data  <-> /var/lib/postgresql/data
```

- Docker creates and manages the storage directory.
- You don't need to know or care about the host path.
- Data survives container removal. Only deleted with explicit `docker volume rm`.

### Practical Examples

#### Example 1: PostgreSQL with persistent data

```yaml
version: '3.8'
services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_PASSWORD: secret
      POSTGRES_DB: myapp
    volumes:
      - pgdata:/var/lib/postgresql/data
    ports:
      - "5432:5432"

volumes:
  pgdata:
```

```bash
# Start and insert data
docker-compose up -d
docker exec -it <container> psql -U postgres -d myapp -c "CREATE TABLE users (id serial, name text);"
docker exec -it <container> psql -U postgres -d myapp -c "INSERT INTO users (name) VALUES ('Alice');"

# Remove the container completely
docker-compose down

# Start fresh container — data is still there
docker-compose up -d
docker exec -it <container> psql -U postgres -d myapp -c "SELECT * FROM users;"
# Output: Alice is still there
```

#### Example 2: Redis cache with persistence

```yaml
version: '3.8'
services:
  redis:
    image: redis:7-alpine
    command: redis-server --appendonly yes
    volumes:
      - redis-data:/data

volumes:
  redis-data:
```

#### Example 3: Sharing a volume between two containers

```yaml
version: '3.8'
services:
  writer:
    build: .
    volumes:
      - shared-data:/data           # writes to /data/events.log

  reader:
    image: alpine
    volumes:
      - shared-data:/data:ro        # reads from /data/events.log (read-only)
    command: tail -f /data/events.log

volumes:
  shared-data:
```

- `writer` produces data, `reader` consumes it.
- `:ro` prevents the reader from accidentally modifying the data.

#### Example 4: The event-logger in this project

```yaml
services:
  event-logger:
    build: .
    volumes:
      - event-data:/data            # app writes to /data/events.log

volumes:
  event-data:
    driver: local
```

The Node.js app writes events to `/data/events.log`. The named volume ensures events persist across container restarts and removals.

### Volume Management Commands

```bash
# List all volumes
docker volume ls

# Inspect a volume (see where it's stored, when created)
docker volume inspect pgdata

# Create a volume manually
docker volume create my-volume

# Remove a specific volume
docker volume rm pgdata

# Remove ALL unused volumes (careful!)
docker volume prune

# Remove containers AND their volumes
docker-compose down -v
```

### When to Use Named Volumes

| Use Case | Why |
|----------|-----|
| Database storage (Postgres, MySQL, Mongo) | Data must survive container lifecycle |
| Application state / uploads | Persistent storage without managing host paths |
| Shared data between containers | Multiple services read/write the same volume |
| Production deployments | Portable, no dependency on host directory structure |

---

## 3. Side-by-Side Comparison

| | Bind Mount | Named Volume |
|---|---|---|
| **Syntax** | `./host/path:/container/path` | `volume-name:/container/path` |
| **Storage location** | You choose (host path) | Docker manages it |
| **Visible in `docker volume ls`** | No | Yes |
| **Survives `docker-compose down`** | N/A (it's your file) | Yes (unless `-v` flag) |
| **Portable across machines** | No (tied to host path) | Yes |
| **Performance** | Slower on Mac/Windows (VM layer) | Generally faster |
| **Best for** | Dev config, source code | Databases, persistent app data |
| **Host can edit directly** | Yes | Possible but not recommended |

---

## 4. Anonymous Volumes

There's a third type — anonymous volumes. These have no name and are mostly used for throwaway data.

```yaml
volumes:
  - /data                       # no name, no host path — anonymous volume
```

```bash
docker run -v /data image       # anonymous volume
```

- Docker assigns a random hash as the volume name.
- Hard to reference later. Usually cleaned up with `docker volume prune`.
- Use case: Prevent a container path from being overwritten by a bind mount (e.g., `node_modules`).

### Common pattern: Protecting node_modules

```yaml
services:
  app:
    build: .
    volumes:
      - ./src:/app/src            # bind mount for hot reload
      - /app/node_modules         # anonymous volume — keeps container's node_modules
```

Without the anonymous volume, the bind mount of `./src` could interfere with `/app/node_modules` inside the container. The anonymous volume preserves the container's own `node_modules`.

---

## 5. Combining Both in a Real Project

A typical setup uses **both** bind mounts and named volumes:

```yaml
version: '3.8'

services:
  api:
    build: .
    volumes:
      - ./src:/app/src                     # bind mount: hot reload in dev
      - ./config/app.yml:/app/config.yml:ro  # bind mount: inject config
      - upload-data:/app/uploads           # named volume: persist user uploads
    ports:
      - "3000:3000"

  postgres:
    image: postgres:15
    environment:
      POSTGRES_PASSWORD: secret
    volumes:
      - pgdata:/var/lib/postgresql/data    # named volume: persist DB

  nginx:
    image: nginx:alpine
    volumes:
      - ./nginx.conf:/etc/nginx/conf.d/default.conf:ro  # bind mount: custom config
    ports:
      - "80:80"

volumes:
  pgdata:
  upload-data:
```

**Summary of what's happening:**
- `./src:/app/src` — Bind mount for development hot-reloading
- `./config/app.yml` — Bind mount for injecting config without rebuilding
- `upload-data:/app/uploads` — Named volume so user uploads persist
- `pgdata:/var/lib/postgresql/data` — Named volume so database survives restarts
- `./nginx.conf` — Bind mount to swap in custom Nginx config

---

## 6. Quick Reference

```bash
# Bind mount (host path : container path)
docker run -v $(pwd)/data:/data myimage

# Named volume (volume name : container path)
docker run -v myvolume:/data myimage

# Read-only mount (either type)
docker run -v $(pwd)/config:/config:ro myimage

# Anonymous volume
docker run -v /data myimage

# List volumes
docker volume ls

# Inspect volume
docker volume inspect myvolume

# Remove volume
docker volume rm myvolume

# Remove all unused volumes
docker volume prune
```

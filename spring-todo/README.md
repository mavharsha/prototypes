# Spring Todo Application

A minimal Spring Boot application with bare minimum dependencies.

## Requirements

- Java 17 or higher
- Maven 3.6+

## Dependencies

- `spring-boot-starter-web` - For building REST APIs
- `spring-boot-starter-data-jpa` - For JPA/Hibernate support
- `spring-boot-starter-actuator` - For monitoring and management
- `h2` - H2 in-memory database (development)
- `mysql-connector-j` - MySQL JDBC driver (production)
- `spring-boot-starter-test` - For testing

## Features

- ✅ RESTful API for Users and Todos
- ✅ Service layer architecture
- ✅ Multi-environment support (dev/prod)
- ✅ H2 in-memory database for development
- ✅ MySQL support for production
- ✅ Spring Boot Actuator for monitoring
- ✅ **Request/Response logging** - All API calls are logged with details
- ✅ **CORS enabled** - Supports requests from localhost:4200 (Angular/React/Vue)
- ✅ Docker Compose support for MySQL

## Database Configuration

This application supports **two profiles**:

### 🔧 Development Profile (`dev`)
- Uses **H2 in-memory database**
- Database created on startup, destroyed on shutdown
- H2 Console enabled for debugging

### 🚀 Production Profile (`prod`)
- Uses **MySQL database**
- Persistent data storage
- H2 Console disabled

## Running the Application

### Development Mode (H2 Database)

```bash
mvn spring-boot:run
```

Or explicitly specify dev profile:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**H2 Console Access** (Development only):
1. Open browser: http://localhost:8080/h2-console
2. Use these login settings:
   - **JDBC URL**: `jdbc:h2:mem:tododb` ⚠️ (Important: must be exactly this)
   - **Username**: `sa`
   - **Password**: (leave empty)
3. Click "Connect"

### Production Mode (MySQL Database)

#### Step 1: Start MySQL Database

Using Docker Compose:
```bash
docker-compose up -d
```

Or using Docker directly:
```bash
docker run -d \
  --name spring-todo-mysql \
  -e MYSQL_ROOT_PASSWORD=mypassword \
  -e MYSQL_DATABASE=todo_db \
  -p 3306:3306 \
  mysql:8.0
```

#### Step 2: Run Application with Production Profile

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

Or build and run JAR:
```bash
mvn clean package
java -jar target/spring-todo-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

### Switching Profiles

You can change the active profile by:

1. **In `application.properties`**:
   ```properties
   spring.profiles.active=prod
   ```

2. **Command Line**:
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=prod
   ```

3. **Environment Variable**:
   ```bash
   export SPRING_PROFILES_ACTIVE=prod
   mvn spring-boot:run
   ```

### Database Configuration Files

- `application.properties` - Common configuration
- `application-dev.properties` - Development (H2) configuration
- `application-prod.properties` - Production (MySQL) configuration

## Request/Response Logging

All API requests and responses are automatically logged with:
- HTTP method and URI
- Request/Response headers (sensitive headers excluded)
- Request/Response body
- HTTP status code
- Request duration (processing time)

### Log Files

- **Console**: All logs appear in terminal
- **`logs/spring-todo.log`**: Application logs (rotates daily)
- **`logs/requests.log`**: Request/response logs only (rotates daily)

### Example Log Output

```
========== Incoming Request ==========
Method: POST
URI: /api/users
Headers: {content-type=application/json, host=localhost:8080}
Request Body: {"userName": "John Doe"}
======================================

========== Outgoing Response ==========
Method: POST
URI: /api/users
Status: 201
Duration: 45 ms
Headers: {Content-Type=application/json}
Response Body: {"userId":1,"userName":"John Doe"}
=======================================
```

### Configuration

**Adjust log verbosity** in profile properties files:
```properties
# INFO (default) - Logs all requests
# WARN - Logs only slow/error requests
# ERROR - Logs only failed requests
# OFF - Disable logging
logging.level.com.example.springtodo.filter.RequestResponseLoggingFilter=INFO
```

**Security**: Sensitive headers (`Authorization`, `Cookie`) are automatically excluded from logs.

See **LOGGING_GUIDE.md** for detailed information.

## CORS Configuration

CORS (Cross-Origin Resource Sharing) is enabled to allow requests from frontend applications.

### Allowed Origin
- `http://localhost:4200` - Typically used by Angular development server

### Allowed Methods
- GET, POST, PUT, PATCH, DELETE, OPTIONS

### Configuration
The CORS configuration is in `WebConfig.java`. To add more origins:

```java
.allowedOrigins(
    "http://localhost:4200",
    "http://localhost:3000",  // React default
    "http://localhost:5173"   // Vite default
)
```

### Testing CORS
```bash
# Test with curl (simulating browser preflight request)
curl -H "Origin: http://localhost:4200" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: Content-Type" \
  -X OPTIONS http://localhost:8080/api/users -v
```

## Testing the Application

```bash
mvn test
```

## API Endpoints

### Health Check
- `GET /api/health` - Simple health check endpoint

### User API
- `GET /api/users` - Get all users
- `GET /api/users/{id}` - Get user by ID
- `POST /api/users` - Create new user
- `PUT /api/users/{id}` - Update user
- `DELETE /api/users/{id}` - Delete user

### Todo API
- `GET /api/todos` - Get all todos
- `GET /api/todos/{id}` - Get todo by ID
- `GET /api/todos/user/{userId}` - Get all todos for a specific user
- `POST /api/todos` - Create new todo
- `PUT /api/todos/{id}` - Update todo
- `PATCH /api/todos/{id}/complete?completed=true` - Mark todo as complete/incomplete
- `DELETE /api/todos/{id}` - Delete todo

## Example Usage

### Create a User
```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"userName": "John Doe"}'
```
**Note**: `userId` is auto-generated by the database.

### Create a Todo
```bash
curl -X POST http://localhost:8080/api/todos \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "todoTask": "Learn Spring Boot", "isCompleted": false}'
```
**Note**: `todoId` is auto-generated by the database.

### Get All Todos for a User
```bash
curl http://localhost:8080/api/todos/user/1
```

### Mark Todo as Complete
```bash
curl -X PATCH http://localhost:8080/api/todos/1/complete?completed=true
```

## Actuator Endpoints (Monitoring & Management)

Spring Boot Actuator provides production-ready features for monitoring and managing your application.

### Development Mode (All endpoints exposed)

**Health Check** (Detailed):
```bash
curl http://localhost:8080/actuator/health
```

**Application Info**:
```bash
curl http://localhost:8080/actuator/info
```

**Metrics**:
```bash
curl http://localhost:8080/actuator/metrics
curl http://localhost:8080/actuator/metrics/jvm.memory.used
```

**Environment Variables**:
```bash
curl http://localhost:8080/actuator/env
```

**List All Endpoints**:
```bash
curl http://localhost:8080/actuator
```

**Other Available Endpoints** (Dev only):
- `/actuator/beans` - All Spring beans
- `/actuator/configprops` - Configuration properties
- `/actuator/loggers` - Logger configuration
- `/actuator/mappings` - All @RequestMapping paths
- `/actuator/threaddump` - Thread dump
- `/actuator/heapdump` - Heap dump (downloads file)

### Production Mode (Limited endpoints for security)

Only these endpoints are exposed in production:
- `/actuator/health` - Health status
- `/actuator/info` - Application information
- `/actuator/metrics` - Application metrics
- `/actuator/prometheus` - Prometheus metrics format

### Health Check Response Example

**Development:**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "H2",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 499963174912,
        "free": 123456789012,
        "threshold": 10485760
      }
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

**Production:**
```json
{
  "status": "UP"
}
```

### Metrics Available

- **JVM Metrics**: Memory, GC, threads
- **System Metrics**: CPU, file descriptors
- **Application Metrics**: HTTP requests, response times
- **Database Metrics**: Connection pool, query performance
- **Custom Metrics**: Can be added as needed

### Integration with Monitoring Tools

The actuator endpoints can be integrated with:
- **Prometheus** - `/actuator/prometheus`
- **Grafana** - Visualize metrics
- **Spring Boot Admin** - UI for managing Spring Boot applications
- **New Relic, Datadog, etc.** - APM tools

## Building

```bash
mvn clean package
```

The executable JAR will be in the `target/` directory.


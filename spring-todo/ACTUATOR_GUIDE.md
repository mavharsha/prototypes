# Spring Boot Actuator Guide

Spring Boot Actuator provides production-ready features to help you monitor and manage your application.

## What is Spring Boot Actuator?

Actuator adds several endpoints to your application that provide:
- **Health checks** - Database, disk space, custom health indicators
- **Metrics** - JVM, HTTP requests, database connections
- **Application info** - Version, build info, git commit
- **Environment details** - Configuration properties, environment variables
- **Thread dumps** - Current state of application threads
- **Loggers** - View and modify log levels at runtime

## Base URL

All actuator endpoints are available at:
```
http://localhost:8080/actuator
```

## Environment-Specific Configuration

### Development (`dev` profile)
- **All endpoints exposed** - Maximum visibility for debugging
- **Detailed health information** - Shows all components
- **Environment values visible** - Configuration details accessible

### Production (`prod` profile)
- **Limited endpoints** - Only health, info, metrics, prometheus
- **Minimal health details** - Only shows overall status
- **Secure by default** - Sensitive information hidden

## Available Endpoints

### 🟢 Core Endpoints (Available in all profiles)

#### Health Check
```bash
GET http://localhost:8080/actuator/health
```

**Development Response:**
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
        "free": 186238754816,
        "threshold": 10485760,
        "path": "C:\\Users\\mamil\\Desktop\\code\\prototypes\\spring-todo\\.",
        "exists": true
      }
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

**Production Response:**
```json
{
  "status": "UP"
}
```

#### Info
```bash
GET http://localhost:8080/actuator/info
```

**Response:**
```json
{
  "app": {
    "name": "Spring Todo Application",
    "description": "A Spring Boot todo application with H2/MySQL database",
    "version": "0.0.1-SNAPSHOT",
    "java": {
      "version": "17"
    }
  }
}
```

#### Metrics
```bash
GET http://localhost:8080/actuator/metrics
```

**List all available metrics:**
```json
{
  "names": [
    "jvm.memory.used",
    "jvm.memory.max",
    "jvm.gc.pause",
    "process.uptime",
    "http.server.requests",
    "system.cpu.usage",
    "jdbc.connections.active",
    ...
  ]
}
```

**Get specific metric:**
```bash
GET http://localhost:8080/actuator/metrics/jvm.memory.used
GET http://localhost:8080/actuator/metrics/http.server.requests
GET http://localhost:8080/actuator/metrics/jdbc.connections.active
```

#### Prometheus (for monitoring integration)
```bash
GET http://localhost:8080/actuator/prometheus
```

### 🔵 Development-Only Endpoints

#### List All Endpoints
```bash
GET http://localhost:8080/actuator
```

#### Beans
Shows all Spring beans in the application context:
```bash
GET http://localhost:8080/actuator/beans
```

#### Environment
Shows all environment properties:
```bash
GET http://localhost:8080/actuator/env
GET http://localhost:8080/actuator/env/spring.datasource.url
```

#### Configuration Properties
Shows all @ConfigurationProperties:
```bash
GET http://localhost:8080/actuator/configprops
```

#### Request Mappings
Shows all @RequestMapping paths:
```bash
GET http://localhost:8080/actuator/mappings
```

#### Loggers
View and modify logger levels:
```bash
# Get all loggers
GET http://localhost:8080/actuator/loggers

# Get specific logger
GET http://localhost:8080/actuator/loggers/com.example.springtodo

# Change log level at runtime (POST request)
POST http://localhost:8080/actuator/loggers/com.example.springtodo
Content-Type: application/json

{
  "configuredLevel": "DEBUG"
}
```

#### Thread Dump
Get current thread information:
```bash
GET http://localhost:8080/actuator/threaddump
```

#### Heap Dump
Download heap dump for memory analysis:
```bash
GET http://localhost:8080/actuator/heapdump
```
*Downloads a `.hprof` file that can be analyzed with tools like VisualVM or Eclipse MAT*

#### Conditions Report
Shows autoconfiguration report:
```bash
GET http://localhost:8080/actuator/conditions
```

## Useful Metrics

### JVM Metrics
```bash
# Memory usage
curl http://localhost:8080/actuator/metrics/jvm.memory.used
curl http://localhost:8080/actuator/metrics/jvm.memory.max

# Garbage collection
curl http://localhost:8080/actuator/metrics/jvm.gc.pause

# Threads
curl http://localhost:8080/actuator/metrics/jvm.threads.live
curl http://localhost:8080/actuator/metrics/jvm.threads.peak
```

### HTTP Metrics
```bash
# All HTTP requests
curl http://localhost:8080/actuator/metrics/http.server.requests

# Filter by URI
curl "http://localhost:8080/actuator/metrics/http.server.requests?tag=uri:/api/users"

# Filter by status
curl "http://localhost:8080/actuator/metrics/http.server.requests?tag=status:200"
```

### Database Metrics
```bash
# Active connections
curl http://localhost:8080/actuator/metrics/jdbc.connections.active

# Connection pool
curl http://localhost:8080/actuator/metrics/hikaricp.connections
```

### System Metrics
```bash
# CPU usage
curl http://localhost:8080/actuator/metrics/system.cpu.usage
curl http://localhost:8080/actuator/metrics/process.cpu.usage

# Uptime
curl http://localhost:8080/actuator/metrics/process.uptime
```

## Security Considerations

### Production Best Practices

1. **Limit exposed endpoints**:
   ```properties
   management.endpoints.web.exposure.include=health,info,metrics
   ```

2. **Secure actuator endpoints** (add Spring Security):
   ```java
   @Bean
   public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
       http
           .authorizeHttpRequests(auth -> auth
               .requestMatchers("/actuator/**").hasRole("ADMIN")
               .anyRequest().permitAll()
           );
       return http.build();
   }
   ```

3. **Use different port** for management:
   ```properties
   management.server.port=9090
   ```

4. **Hide detailed health information**:
   ```properties
   management.endpoint.health.show-details=never
   ```

## Integration with Monitoring Tools

### Prometheus + Grafana

1. **Enable Prometheus endpoint** (already enabled in prod):
   ```properties
   management.endpoints.web.exposure.include=prometheus
   ```

2. **Configure Prometheus** (`prometheus.yml`):
   ```yaml
   scrape_configs:
     - job_name: 'spring-todo'
       metrics_path: '/actuator/prometheus'
       static_configs:
         - targets: ['localhost:8080']
   ```

3. **Import Grafana dashboard**:
   - Use dashboard ID: 4701 (JVM Micrometer)
   - Or create custom dashboards

### Spring Boot Admin

1. **Add dependency**:
   ```xml
   <dependency>
       <groupId>de.codecentric</groupId>
       <artifactId>spring-boot-admin-starter-client</artifactId>
   </dependency>
   ```

2. **Configure client**:
   ```properties
   spring.boot.admin.client.url=http://localhost:8081
   ```

### Docker Health Check

Use actuator health endpoint in Docker:
```dockerfile
HEALTHCHECK --interval=30s --timeout=3s \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
```

## Custom Health Indicators

You can create custom health indicators:

```java
@Component
public class CustomHealthIndicator implements HealthIndicator {
    
    @Override
    public Health health() {
        // Perform custom health check
        boolean healthy = checkSomething();
        
        if (healthy) {
            return Health.up()
                .withDetail("customCheck", "Everything is fine")
                .build();
        } else {
            return Health.down()
                .withDetail("customCheck", "Something went wrong")
                .build();
        }
    }
    
    private boolean checkSomething() {
        // Your custom logic
        return true;
    }
}
```

## Custom Metrics

Add custom metrics to your application:

```java
@Service
public class TodoService {
    
    private final MeterRegistry meterRegistry;
    private final Counter todoCreatedCounter;
    
    public TodoService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.todoCreatedCounter = Counter.builder("todos.created")
            .description("Number of todos created")
            .register(meterRegistry);
    }
    
    public Todo createTodo(Todo todo) {
        // ... save logic
        todoCreatedCounter.increment();
        return savedTodo;
    }
}
```

## Testing Actuator Endpoints

```bash
# Quick health check
curl http://localhost:8080/actuator/health

# Pretty print JSON (with jq)
curl http://localhost:8080/actuator/health | jq

# Save metrics to file
curl http://localhost:8080/actuator/metrics > metrics.json

# Check if app is ready
curl -f http://localhost:8080/actuator/health || echo "App is down"
```

## Common Use Cases

1. **Container orchestration health checks**: Use `/actuator/health`
2. **Performance monitoring**: Use `/actuator/metrics`
3. **Troubleshooting**: Use `/actuator/env`, `/actuator/beans`, `/actuator/mappings`
4. **Runtime log level changes**: Use `/actuator/loggers`
5. **Memory analysis**: Use `/actuator/heapdump`
6. **Integration with APM tools**: Use `/actuator/prometheus`

## Troubleshooting

### Endpoints not accessible?
- Check `management.endpoints.web.exposure.include` in properties
- Verify the active profile (dev vs prod)

### 404 on actuator endpoints?
- Ensure actuator dependency is in `pom.xml`
- Check base path: default is `/actuator`

### Health shows DOWN?
- Check database connection
- Check disk space
- Review component details in dev mode

### Want more endpoints in production?
- Update `application-prod.properties`:
  ```properties
  management.endpoints.web.exposure.include=health,info,metrics,loggers
  ```


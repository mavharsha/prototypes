# Request/Response Logging Guide

This application includes comprehensive request/response logging through a custom filter.

## Overview

The `RequestResponseLoggingFilter` logs all incoming HTTP requests and outgoing responses with detailed information:
- HTTP method and URI
- Query parameters
- Request/Response headers
- Request/Response body
- HTTP status code
- Request duration (processing time)

## How It Works

### Filter Implementation

The filter uses:
- **`@Component`** - Auto-registered as a Spring bean
- **`@Order(1)`** - Executes first in the filter chain
- **`ContentCachingRequestWrapper`** - Allows reading request body multiple times
- **`ContentCachingResponseWrapper`** - Allows reading response body multiple times

### What Gets Logged

#### Request Logging
```
========== Incoming Request ==========
Method: POST
URI: /api/users
Headers: {content-type=application/json, host=localhost:8080}
Request Body: {"userName": "John Doe"}
======================================
```

#### Response Logging
```
========== Outgoing Response ==========
Method: POST
URI: /api/users
Status: 201
Duration: 45 ms
Headers: {Content-Type=application/json}
Response Body: {"userId":1,"userName":"John Doe"}
=======================================
```

## Log Files

Logs are written to multiple destinations:

### 1. Console Output
All logs are printed to the console (standard output).

### 2. Application Log File
**Location**: `logs/spring-todo.log`
- Contains all application logs
- Rotates daily
- Keeps 30 days of history
- Files: `spring-todo-{yyyy-MM-dd}.log`

### 3. Request Log File
**Location**: `logs/requests.log`
- Contains only request/response logs
- Rotates daily
- Keeps 30 days of history
- Files: `requests-{yyyy-MM-dd}.log`

## Configuration

### Log Levels by Environment

#### Development (`dev` profile)
```properties
# Verbose logging
logging.level.com.example.springtodo.filter.RequestResponseLoggingFilter=INFO
```

#### Production (`prod` profile)
```properties
# Can be adjusted to WARN or ERROR for less verbosity
logging.level.com.example.springtodo.filter.RequestResponseLoggingFilter=INFO
```

### Adjusting Log Verbosity

Edit the properties file for your profile:

**Less verbose** (only errors):
```properties
logging.level.com.example.springtodo.filter.RequestResponseLoggingFilter=ERROR
```

**More verbose** (debug level):
```properties
logging.level.com.example.springtodo.filter.RequestResponseLoggingFilter=DEBUG
```

**Turn off completely**:
```properties
logging.level.com.example.springtodo.filter.RequestResponseLoggingFilter=OFF
```

## Security Features

### Sensitive Data Protection

The filter automatically **excludes sensitive headers** from logs:
- `Authorization` header (Bearer tokens, Basic auth)
- `Cookie` header (session cookies, auth cookies)

**Example**: If a request includes `Authorization: Bearer xyz123`, this will NOT appear in logs.

### Customizing Sensitive Data Filtering

To exclude additional headers, modify the filter:

```java
// In RequestResponseLoggingFilter.java
private Map<String, String> getHeaders(HttpServletRequest request) {
    // Add more sensitive headers here
    if (!headerName.equalsIgnoreCase("authorization") &&
        !headerName.equalsIgnoreCase("cookie") &&
        !headerName.equalsIgnoreCase("x-api-key")) {  // Add custom headers
        headers.put(headerName, headerValue);
    }
}
```

To exclude sensitive request body fields (e.g., passwords):
```java
private String getRequestBody(ContentCachingRequestWrapper request) {
    // ... existing code ...
    if (buf.length > 0) {
        try {
            String body = new String(buf, 0, buf.length, request.getCharacterEncoding());
            // Mask sensitive fields
            return maskSensitiveData(body);
        } catch (UnsupportedEncodingException e) {
            return "[Unable to parse request body]";
        }
    }
    return "";
}

private String maskSensitiveData(String body) {
    // Simple example - use JSON parsing for production
    return body.replaceAll("\"password\"\\s*:\\s*\"[^\"]*\"", "\"password\":\"***\"");
}
```

## Performance Considerations

### Impact on Performance

The filter has minimal performance impact:
- Request wrapping: ~1-2ms overhead
- Logging I/O: Asynchronous (doesn't block request)
- Memory: Small buffer for request/response bodies

### Optimization Tips

1. **Disable in high-traffic production**:
   ```properties
   logging.level.com.example.springtodo.filter.RequestResponseLoggingFilter=OFF
   ```

2. **Limit body logging size**:
   ```java
   private String getRequestBody(ContentCachingRequestWrapper request) {
       byte[] buf = request.getContentAsByteArray();
       if (buf.length > 0) {
           int maxLength = 1000; // Log only first 1KB
           int length = Math.min(buf.length, maxLength);
           // ... rest of code
       }
   }
   ```

3. **Exclude specific paths** (e.g., actuator, static resources):
   ```java
   @Override
   public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
           throws IOException, ServletException {
       
       HttpServletRequest request = (HttpServletRequest) servletRequest;
       String uri = request.getRequestURI();
       
       // Skip logging for certain paths
       if (uri.startsWith("/actuator") || uri.startsWith("/static")) {
           filterChain.doFilter(servletRequest, servletResponse);
           return;
       }
       
       // ... rest of logging code
   }
   ```

## Use Cases

### 1. Debugging API Issues
View complete request/response cycle to troubleshoot issues:
```bash
tail -f logs/requests.log
```

### 2. Performance Monitoring
Check request duration to identify slow endpoints:
```bash
grep "Duration:" logs/requests.log | sort -t: -k3 -n
```

### 3. Audit Trail
Track all API calls for compliance:
```bash
grep "POST" logs/requests.log
```

### 4. Error Analysis
Filter for failed requests:
```bash
grep "Status: [45]" logs/requests.log
```

## Testing the Logging

### 1. Start the application
```bash
mvn spring-boot:run
```

### 2. Make a test request
```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"userName": "Test User"}'
```

### 3. Check the logs

**Console output**:
You'll see the request/response logs in your terminal.

**Log file**:
```bash
cat logs/requests.log
```

## Advanced: Structured Logging (JSON)

For production environments, consider JSON-formatted logs for easier parsing:

### Add dependency (pom.xml):
```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

### Update logback-spring.xml:
```xml
<appender name="JSON_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/requests.json</file>
    <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
        <fileNamePattern>logs/requests-%d{yyyy-MM-dd}.json</fileNamePattern>
        <maxHistory>30</maxHistory>
    </rollingPolicy>
</appender>
```

This creates machine-readable JSON logs perfect for:
- ELK Stack (Elasticsearch, Logstash, Kibana)
- Splunk
- CloudWatch Logs
- Other log aggregation tools

## Best Practices

1. ✅ **Use in development** - Essential for debugging
2. ✅ **Careful in production** - Can generate large log files
3. ✅ **Exclude sensitive data** - Never log passwords, API keys, tokens
4. ✅ **Rotate logs** - Prevent disk space issues (already configured)
5. ✅ **Monitor log file size** - Set up alerts for unusual growth
6. ✅ **Use log aggregation** - For distributed systems, centralize logs

## Troubleshooting

### Logs not appearing?
- Check log level: `logging.level.com.example.springtodo.filter.RequestResponseLoggingFilter=INFO`
- Verify filter is registered: Look for `@Component` annotation

### Empty request/response bodies?
- Ensure using `ContentCachingRequestWrapper` and `ContentCachingResponseWrapper`
- Call `copyBodyToResponse()` at the end

### Performance issues?
- Reduce log level to WARN or ERROR
- Exclude high-traffic endpoints
- Limit body size logging

### Log files not created?
- Check file permissions
- Ensure `logs/` directory exists (created automatically)
- Check disk space

## Comparison with Alternatives

| Method | Pros | Cons |
|--------|------|------|
| **Filter** ✅ (Our approach) | Complete control, reads body, measures duration | Requires wrapper classes |
| **Interceptor** | Spring-specific, access to handler | Cannot read raw request body |
| **AOP** | Clean separation, method-level | Misses filter-level errors |
| **Spring Boot Logging** | Built-in | Limited customization |

## Summary

The request/response logging filter provides:
- ✅ Complete visibility into API traffic
- ✅ Security-conscious (excludes sensitive headers)
- ✅ Performance metrics (request duration)
- ✅ Separate log files for easy analysis
- ✅ Environment-specific configuration
- ✅ Production-ready with log rotation

All logs are automatically written to both console and files with daily rotation!


# CORS Configuration Guide

This guide explains the CORS (Cross-Origin Resource Sharing) configuration in the Spring Boot Todo application.

## What is CORS?

CORS is a security feature implemented by browsers that restricts web pages from making requests to a different domain than the one serving the web page. This is called the "Same-Origin Policy."

### The Problem

Without CORS configuration:
- Your Angular app runs on `http://localhost:4200`
- Your Spring Boot API runs on `http://localhost:8080`
- Browser blocks API requests due to different origins (different ports)
- You get errors like: `Access to XMLHttpRequest has been blocked by CORS policy`

### The Solution

Configure the Spring Boot backend to explicitly allow requests from `http://localhost:4200`.

## Current Configuration

### Allowed Origin
```
http://localhost:4200
```
This is the default port for Angular development server (`ng serve`).

### Allowed Methods
```
GET, POST, PUT, PATCH, DELETE, OPTIONS
```
All standard HTTP methods needed for RESTful APIs.

### Allowed Headers
```
* (all headers)
```
Allows any custom headers from the client.

### Credentials
```
allowCredentials: true
```
Allows sending cookies and authorization headers.

### Max Age
```
3600 seconds (1 hour)
```
Browser caches the preflight response for 1 hour.

## Configuration File

The CORS configuration is in `src/main/java/com/example/springtodo/config/WebConfig.java`:

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:4200")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
```

## Common Frontend Frameworks

### Angular (Default: Port 4200)
```bash
ng serve
# Runs on http://localhost:4200
```
✅ **Already configured!**

### React (Default: Port 3000)
```bash
npm start
# Runs on http://localhost:3000
```
To support React, add the origin:
```java
.allowedOrigins("http://localhost:4200", "http://localhost:3000")
```

### Vue.js (Default: Port 8080 or 5173 with Vite)
```bash
npm run serve  # Port 8080
npm run dev    # Port 5173 (Vite)
```
To support Vue:
```java
.allowedOrigins("http://localhost:4200", "http://localhost:5173")
```

### Multiple Origins Example
```java
@Override
public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
            .allowedOrigins(
                "http://localhost:4200",  // Angular
                "http://localhost:3000",  // React
                "http://localhost:5173",  // Vue/Vite
                "http://localhost:8081"   // Custom
            )
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);
}
```

## Production Configuration

### Environment-Specific CORS

For production, you should restrict origins to your actual domain:

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${cors.allowed.origins}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
```

Then in your properties files:

**application-dev.properties:**
```properties
cors.allowed.origins=http://localhost:4200,http://localhost:3000
```

**application-prod.properties:**
```properties
cors.allowed.origins=https://yourdomain.com,https://www.yourdomain.com
```

## Testing CORS

### Method 1: Using Browser DevTools

1. Open your frontend application (e.g., Angular on `http://localhost:4200`)
2. Open browser DevTools (F12) → Network tab
3. Make an API request
4. Check the response headers for:
   ```
   Access-Control-Allow-Origin: http://localhost:4200
   Access-Control-Allow-Credentials: true
   ```

### Method 2: Using cURL (Preflight Request)

```bash
# Simulate browser preflight OPTIONS request
curl -H "Origin: http://localhost:4200" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: Content-Type" \
  -X OPTIONS \
  http://localhost:8080/api/users \
  -v
```

**Expected Response Headers:**
```
Access-Control-Allow-Origin: http://localhost:4200
Access-Control-Allow-Methods: GET,POST,PUT,PATCH,DELETE,OPTIONS
Access-Control-Allow-Headers: *
Access-Control-Allow-Credentials: true
Access-Control-Max-Age: 3600
```

### Method 3: Using JavaScript in Browser Console

```javascript
fetch('http://localhost:8080/api/users')
  .then(response => response.json())
  .then(data => console.log(data))
  .catch(error => console.error('CORS Error:', error));
```

If CORS is configured correctly, you'll see the data. If not, you'll see a CORS error.

## Frontend Integration Examples

### Angular HttpClient

```typescript
import { HttpClient } from '@angular/common/http';

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private apiUrl = 'http://localhost:8080/api/users';

  constructor(private http: HttpClient) {}

  getUsers() {
    return this.http.get<User[]>(this.apiUrl);
  }

  createUser(user: User) {
    return this.http.post<User>(this.apiUrl, user);
  }
}
```

### React Axios

```javascript
import axios from 'axios';

const API_URL = 'http://localhost:8080/api/users';

export const getUsers = async () => {
  const response = await axios.get(API_URL);
  return response.data;
};

export const createUser = async (user) => {
  const response = await axios.post(API_URL, user);
  return response.data;
};
```

### Vue.js Fetch API

```javascript
export default {
  data() {
    return {
      users: []
    }
  },
  async mounted() {
    const response = await fetch('http://localhost:8080/api/users');
    this.users = await response.json();
  }
}
```

## CORS with Authentication

If using JWT tokens or session cookies:

```java
@Override
public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
            .allowedOrigins("http://localhost:4200")
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .exposedHeaders("Authorization")  // Expose custom headers
            .allowCredentials(true)           // Required for cookies/auth
            .maxAge(3600);
}
```

**Frontend must include credentials:**

**Angular:**
```typescript
this.http.get(url, { withCredentials: true })
```

**Axios:**
```javascript
axios.get(url, { withCredentials: true })
```

**Fetch:**
```javascript
fetch(url, { credentials: 'include' })
```

## Common Issues & Solutions

### Issue 1: CORS Error Still Appears

**Symptom:**
```
Access to XMLHttpRequest at 'http://localhost:8080/api/users' from origin 
'http://localhost:4200' has been blocked by CORS policy
```

**Solutions:**
1. ✅ Verify the backend is running
2. ✅ Check the origin is exactly `http://localhost:4200` (no trailing slash)
3. ✅ Restart the Spring Boot application after config changes
4. ✅ Clear browser cache (Ctrl+Shift+Delete)

### Issue 2: Credentials Error

**Symptom:**
```
The value of the 'Access-Control-Allow-Origin' header must not be the wildcard '*' 
when the request's credentials mode is 'include'
```

**Solution:**
Don't use `"*"` for allowed origins when `allowCredentials(true)` is set. Use specific origins:
```java
.allowedOrigins("http://localhost:4200")  // ✅ Correct
.allowedOrigins("*")                       // ❌ Wrong with credentials
```

### Issue 3: Method Not Allowed

**Symptom:**
```
Method PUT is not allowed by Access-Control-Allow-Methods
```

**Solution:**
Ensure the method is in the allowed methods list:
```java
.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
```

### Issue 4: Custom Header Blocked

**Symptom:**
```
Request header 'X-Custom-Header' is not allowed by Access-Control-Allow-Headers
```

**Solution:**
Either allow all headers:
```java
.allowedHeaders("*")
```

Or specify the custom header:
```java
.allowedHeaders("Content-Type", "Authorization", "X-Custom-Header")
```

## Alternative: Controller-Level CORS

Instead of global configuration, you can use `@CrossOrigin` on specific controllers:

```java
@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "http://localhost:4200")
public class UserController {
    // ... controller methods
}
```

**Pros:** Fine-grained control per controller
**Cons:** Repetitive, harder to maintain

**Recommendation:** Use global configuration (WebConfig) for consistency.

## Security Best Practices

### Development
✅ Allow `localhost` origins
✅ Allow all methods for flexibility
✅ Allow credentials for testing auth

### Production
✅ **Only allow specific domains** - Never use `"*"`
✅ **Restrict methods** - Only allow methods you actually use
✅ **Restrict headers** - Specify exact headers needed
✅ **Use HTTPS** - `https://yourdomain.com`
✅ **Consider rate limiting** - Prevent abuse
✅ **Monitor CORS errors** - Track unauthorized access attempts

### Example Production Config

```java
@Override
public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")  // Only API endpoints
            .allowedOrigins("https://yourdomain.com")  // HTTPS only
            .allowedMethods("GET", "POST", "PUT", "DELETE")  // No OPTIONS needed
            .allowedHeaders("Content-Type", "Authorization")  // Specific headers
            .allowCredentials(true)
            .maxAge(86400);  // 24 hours
}
```

## CORS with Spring Security

If you add Spring Security later, you'll need to configure CORS there too:

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.disable())
        // ... other security config
    return http.build();
}

@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(Arrays.asList("http://localhost:4200"));
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE"));
    configuration.setAllowedHeaders(Arrays.asList("*"));
    configuration.setAllowCredentials(true);
    
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
}
```

## Summary

✅ **Current Setup:** CORS is configured to allow `http://localhost:4200`
✅ **All Methods:** GET, POST, PUT, PATCH, DELETE, OPTIONS
✅ **Credentials:** Enabled for auth headers and cookies
✅ **Easy to Extend:** Add more origins in `WebConfig.java`
✅ **Production Ready:** Update origins for production deployment

Your Angular application can now communicate with the Spring Boot API without CORS issues! 🎉

